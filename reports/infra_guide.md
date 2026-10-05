# Building a segmented MikroTik edge for a self-hosted camera VPN, from scratch

A step-by-step guide to build, on a blank **RB750Gr3 (hEX)**, the exact router configuration behind the cameras app: an IPv6-only WireGuard VPN that exposes a single Raspberry Pi to remote viewers, with the home network, the Pi and the IP cameras split into three firewalled zones.

- **RouterOS:** 7.20.2.
- **Internet setup:** the MikroTik sits behind the ISP router, gets a private IPv4 by DHCP and a single IPv6 address by DHCPv6 (no prefix delegation).
- **How to use it:** each step is small and ends with a test. Do not move on until the test passes. Firewall rules that cannot be tested inside this project are explained instead.

---

## Contents

- [Part 0 — Design and prerequisites](#part-0--design-and-prerequisites)
- [Part 1 — Base system and HOME network](#part-1--base-system-and-home-network) (steps 1–6)
- [Part 2 — DMZ and CAM zones (IPv4)](#part-2--dmz-and-cam-zones-ipv4) (steps 7–9)
- [Part 3 — IPv4 firewall](#part-3--ipv4-firewall) (steps 10–12)
- [Part 4 — IPv6 inside the router](#part-4--ipv6-inside-the-router) (steps 13–15)
- [Part 5 — IPv6 firewall](#part-5--ipv6-firewall) (steps 16–19)
- [Part 6 — IPv6 on the WAN](#part-6--ipv6-on-the-wan) (step 20)
- [Part 7 — DNS name and DDNS](#part-7--dns-name-and-ddns) (steps 21–22)
- [Part 8 — Raspberry Pi firewall (ufw)](#part-8--raspberry-pi-firewall-ufw) (steps 23–26)
- [Part 9 — Generating VPN access](#part-9--generating-vpn-access) (steps 27–33)

---

## Part 0 — Design and prerequisites

### 0.1 Topology

```
                     Internet
                         │
                ┌────────┴────────┐
                │   ISP router    │  private IPv4 (NAT), one IPv6 by DHCPv6, no PD
                └────────┬────────┘
                         │ ether1 (WAN)
          ┌──────────────┴──────────────┐
          │      MikroTik RB750Gr3      │── wireguard-ipv6 (VPN, fd00:10::/64, UDP 14079)
          └──┬──────────┬───────────┬───┘
     bridge  │   ether4 │    ether5 │
  (ether2-3) │          │           │
     ┌───────┴──┐ ┌─────┴───────┐ ┌─┴──────────────┐
     │  HOME    │ │   DMZ       │ │   CAM          │
     │ .89.0/24 │ │ .91.0/24    │ │ .90.0/24       │
     │ fd00:30::│ │ fd00:20::/64│ │ no IPv6        │
     │          │ │ Pi ::cafe   │ │ no internet    │
     └──────────┘ └─────────────┘ └──┬─────────────┘
                                     │ unmanaged PoE switch
                                  4 IP cameras
```

### 0.2 Zones and addressing

| Zone | Interface | IPv4 | IPv6 | Trust | Purpose |
|---|---|---|---|---|---|
| WAN | `ether1` | DHCP from the ISP router | DHCPv6 address (`/128`) + RA default route | — | Uplink |
| HOME | `bridge` (`ether2`, `ether3`) | `192.168.89.1/24` | `fd00:30::1/64`, advertised (SLAAC) | Medium | Personal devices, router management |
| DMZ | `ether4` | `192.168.91.1/24` | `fd00:20::1/64`, not advertised | Low, exposed | Raspberry Pi (`192.168.91.2`, `fd00:20::cafe`) running MediaMTX and Samba |
| CAM | `ether5` | `192.168.90.1/24` | none (dropped) | Very low | IP cameras: no internet, only RTSP to the Pi |
| VPN | `wireguard-ipv6` | none | `fd00:10::1/64` | Admins: high. Clients: none | Remote access |

### 0.3 Who reaches what

| From → To | Internet | Router | Pi (DMZ) | Cameras | HOME |
|---|---|---|---|---|---|
| HOME | ✅ IPv4 (NAT44) and IPv6 (NAT66) | ✅ management | ❌ (only through the VPN) | ❌, except the camera web UI for a temporary allow list | — |
| DMZ (Pi) | ✅ IPv4 only (updates) | DHCP, DNS, NTP, ping | — | ✅ TCP 554 (RTSP) | ❌ |
| CAM | ❌, except a temporary allow list | DHCP, NTP | ❌ | (same switch) | ❌ |
| VPN, every peer | ❌ | DNS, ping | ✅ TCP 8889, UDP 8189, ping | ❌ | ❌ |
| VPN, admin | ✅ IPv6 (NAT66) | + SSH | + TCP 22, 80, 443, 445, 8888 | ❌ | ❌ |

VPN peers never talk to each other, and nothing can open a connection toward a VPN peer.

The VPN is **IPv6 only** on purpose. The ISP router does NAT on IPv4, so the MikroTik is not reachable on IPv4 from outside. Its IPv6 address is reachable, so the VPN endpoint is published as an AAAA record kept up to date by a DDNS script.

### 0.4 What you need

- An RB750Gr3 with RouterOS 7.20.2 or newer (same major version).
- A **HOME computer** (macOS or Linux) with Winbox and an SSH client. It is connected to `ether2` or `ether3` and is where every "On the HOME computer" command runs.
- The **Pi**: the Raspberry Pi that lives in the DMZ (`ether4`). Commands marked "On the Pi" run on it.
- The Raspberry Pi, already running MediaMTX, with its network managed by NetworkManager.
- A Cloudflare zone for the DDNS hostname and an API token limited to `Zone → DNS → Edit` on that zone.
- `wireguard-tools`, `jq` and Python 3 on the HOME computer, for Part 9.
- The ISP router must let inbound IPv6 UDP reach the MikroTik on port 14079. Check this in its firewall settings before you start.

### 0.5 Working rules

1. **Use Safe Mode** (`Ctrl+X` in the terminal, or the *Safe Mode* button in Winbox) before pasting any firewall block. If you lock yourself out, the router reverts the change when the session drops.
2. **Select objects by `comment`, `name` or the `print` number.** Filters on interface properties (`[find in-interface=...]`, `[find out-interface=...]`) are not a reliable way to locate a rule, and a `set`/`remove` whose `find` matches nothing does not raise an error. A wrong selector therefore goes unnoticed, while a comment or a number either matches the intended object or visibly matches nothing in `print`.
3. **Save a checkpoint at the end of each part:**

   ```routeros
   /export file=checkpoint-part1
   /system backup save name=checkpoint-part1 password="<passphrase>"
   ```

4. **Placeholders** look like `<THIS>`. Never commit real keys, MACs or tokens.

---

## Part 1 — Base system and HOME network

### Step 1 — Update, then reset

**Update first.** After the reset the router has no internet access until Step 4, so it cannot download anything. Update while it still has a working configuration: a router out of the box has the factory default config, which already gets internet on `ether1`; a router in use has its own.

```routeros
/system package update check-for-updates
# If a newer 7.x is available (the router reboots):
/system package update install
```

After the reboot, upgrade the RouterBOOT firmware to match:

```routeros
/system routerboard print
# If current-firmware differs from upgrade-firmware:
/system routerboard upgrade
/system reboot
```

**Test**

```routeros
/system resource print ; # version 7.20.2 (or newer 7.x), board-name hEX
/system routerboard print ; # current-firmware = upgrade-firmware
```

**Then reset.** Start from an empty configuration, so that nothing from the factory defaults or from an older setup is left behind.

```routeros
/system reset-configuration no-defaults=yes skip-backup=yes
```

After the reboot, the router has no IP address. Connect the HOME computer to `ether2` and open Winbox through the **Neighbors** tab (MAC connection). On the first login, RouterOS asks for a new `admin` password: choose a strong one.

### Step 2 — Clock and NTP client

The hEX has no battery-backed clock. Without NTP, logs get wrong timestamps after a power loss, and the TLS check in the DDNS script (Step 22) can fail.

```routeros
/system clock set time-zone-autodetect=no time-zone-name=America/Sao_Paulo
/system ntp client set enabled=yes
/system ntp client servers
add address=a.ntp.br
add address=b.ntp.br
add address=time.cloudflare.com
```

The NTP client only works once the WAN is up (Step 4). Test it there.

### Step 3 — Interface lists

The firewall is written against zones, not ports. Changing which port belongs to a zone later only means changing the list membership.

```routeros
/interface list
add name=WAN
add name=LAN
add name=VPN
add name=DMZ comment="Rpi: the only host reachable from the VPN"
add name=CAM comment="IP cameras: untrusted firmware, no internet"

/interface list member
add interface=ether1 list=WAN
add interface=ether4 list=DMZ
add interface=ether5 list=CAM
```

`LAN` gets the bridge in Step 5, and `VPN` gets the WireGuard interface in Step 13. In this design, the `VPN` list is not used by any rule: every VPN rule names the interface directly. It is kept so that a second WireGuard interface can be added later without rewriting rules.

**Test**

```routeros
/interface list member print
# Expected: ether1 in WAN, ether4 in DMZ, ether5 in CAM
```

### Step 4 — WAN IPv4 and the router's resolver

```routeros
/ip dhcp-client
add interface=ether1 add-default-route=special-classless use-peer-dns=no

/ip dns
set allow-remote-requests=yes \
    servers=2001:4860:4860::8888,2001:4860:4860::8844,8.8.8.8,1.1.1.1
```

`use-peer-dns=no` keeps the ISP router's resolver out of the path. `allow-remote-requests=yes` turns the router into the DNS server for HOME, DMZ and the VPN. It is not exposed yet: `ether1` only has a private IPv4 behind the ISP router, and IPv6 on the WAN stays off until the IPv6 firewall exists (Part 6). The input firewall in Step 10 restricts who may query it.

**Test**

```routeros
/ip dhcp-client print ; # status=bound
/ping 8.8.8.8 count=3
:put [:resolve example.com]
/system ntp client print ; # status=synchronized after about a minute
/system clock print ; # correct local time
```

### Step 5 — HOME bridge, DHCP and NAT44

Build the whole HOME network first, with only `ether3` in the bridge. The HOME computer is still on `ether2`, so the session stays up:

```routeros
/interface bridge add name=bridge
/interface bridge port add bridge=bridge interface=ether3
/interface list member add interface=bridge list=LAN

/ip address add address=192.168.89.1/24 interface=bridge network=192.168.89.0
/ip pool add name=dhcp_pool1 ranges=192.168.89.2-192.168.89.254
/ip dhcp-server add address-pool=dhcp_pool1 interface=bridge name=dhcp1
/ip dhcp-server network add address=192.168.89.0/24 dns-server=192.168.89.1 \
    gateway=192.168.89.1

/ip firewall nat add action=masquerade chain=srcnat out-interface=ether1 \
    comment="NAT44: LAN -> Internet"
```

Now add `ether2`. **The Winbox session drops at this command**, because the port the HOME computer uses moves into the bridge:

```routeros
/interface bridge port add bridge=bridge interface=ether2
```

Wait until the HOME computer gets a lease from `192.168.89.0/24` (usually a few seconds), then reconnect to Winbox by IP (`192.168.89.1`).

Now pin the bridge MAC. By default, a bridge copies the MAC of one of its ports, and that can change when ports are added or removed. A fixed MAC keeps the bridge's IPv6 link-local address fixed too, and that link-local is the default gateway HOME devices learn from the router advertisements (Step 14).

```routeros
/interface bridge set bridge auto-mac=no \
    admin-mac=[/interface ethernet get ether2 mac-address]
```

**Test** (on the HOME computer)

```bash
# macOS (use your interface name: en0, en1, ...)
ipconfig getifaddr en0             # 192.168.89.x
# Linux (use your interface name: eth0, enp3s0, ...)
ip -4 addr show eth0               # inet 192.168.89.x/24

# Both
ping -c3 192.168.89.1
curl -4 -m5 https://example.com -o /dev/null -w '%{http_code}\n'   # 200
```

```routeros
/interface bridge print proplist=name,auto-mac,admin-mac ; # auto-mac=no
```

### Step 6 — Harden the management plane

Only HOME may discover and manage the router. Every service that is not used is turned off.

```routeros
/ip service
set ftp disabled=yes
set telnet disabled=yes
set www disabled=yes
set api disabled=yes
set api-ssl disabled=yes
set winbox address=192.168.89.0/24
set ssh address=fd00:10::/64,192.168.89.0/24
/ip ssh set strong-crypto=yes

/ip neighbor discovery-settings set discover-interface-list=LAN
/tool mac-server set allowed-interface-list=LAN
/tool mac-server mac-winbox set allowed-interface-list=LAN
/tool mac-server ping set enabled=no
/tool bandwidth-server set enabled=no
```

SSH also accepts `fd00:10::/64` so that VPN admins can manage the router. The IPv6 firewall (Step 17) narrows this down to the admin block (`fd00:10::a:0/112`): clients are in the same `/64`, but they never reach port 22.

**Test** (on the HOME computer)

```bash
ssh admin@192.168.89.1 '/system identity print'   # works
nc -vz -w3 192.168.89.1 23                         # refused (telnet off)
nc -vz -w3 192.168.89.1 80                         # refused (www off)
```

Save checkpoint `part1`.

---

## Part 2 — DMZ and CAM zones (IPv4)

### Step 7 — DMZ for the Raspberry Pi

```routeros
/ip address add address=192.168.91.1/24 interface=ether4 network=192.168.91.0 comment=DMZ
/ip pool add name=pool-dmz ranges=192.168.91.100-192.168.91.199
/ip dhcp-server add address-pool=pool-dmz interface=ether4 lease-time=1d name=dhcp-dmz
/ip dhcp-server network add address=192.168.91.0/24 comment=DMZ \
    dns-server=192.168.91.1 gateway=192.168.91.1 ntp-server=192.168.91.1
```

Plug the Pi into `ether4` and wait for its dynamic lease. It usually takes **30 seconds to 1 minute** to show up; run the `print` again until it does. Then make it static at `192.168.91.2`. `make-static` keeps the MAC and the client-id the Pi actually sent:

```routeros
/ip dhcp-server lease print where server=dhcp-dmz
/ip dhcp-server lease make-static [find server=dhcp-dmz]
/ip dhcp-server lease set [find server=dhcp-dmz] address=192.168.91.2 comment=Rpi

/ip firewall address-list
add address=192.168.91.2 list=rpi comment="Rpi (DMZ fixed lease)"
```

On the Pi, renew the lease (`sudo nmcli con up "<connection>"`) so that it moves to the new address.

**Test**

```routeros
/ip dhcp-server lease print where comment=Rpi ; # 192.168.91.2, status=bound
```

```bash
# On the Pi
ip -4 addr show eth0          # 192.168.91.2/24
ip route | grep default       # via 192.168.91.1
```

There is no firewall yet, so the Pi still reaches everything. Part 3 closes that.

### Step 8 — CAM zone for the cameras

```routeros
/ip address add address=192.168.90.1/24 interface=ether5 network=192.168.90.0 comment=CAM
/ip pool add name=pool-cam ranges=192.168.90.100-192.168.90.199
/ip dhcp-server add address-pool=pool-cam interface=ether5 lease-time=1d name=dhcp-cam
/ip dhcp-server network add address=192.168.90.0/24 comment=CAM \
    dns-server=192.168.90.1 gateway=192.168.90.1 ntp-server=192.168.90.1
```

The cameras get fixed leases in Step 12, the same way as the Pi in Step 7: first a dynamic lease, then `make-static`.

> **Do not connect the cameras yet.** Until Part 3, the CAM zone has internet access. Connect them only after Step 11.

**Test** (with the PoE switch still off)

```routeros
/ip dhcp-server print
# dhcp-cam shows "I" (interface not running): expected with nothing on ether5
```

### Step 9 — NTP server for the zones

The cameras will never have internet access, and the Pi uses the router as its clock source. The router serves the time it gets from Step 2.

```routeros
/system ntp server set enabled=yes
```

**Test** (on the Pi)

```bash
ntpdate -q 192.168.91.1     # or: chronyc sources, after pointing chrony to it
```

Save checkpoint `part2`.

---

## Part 3 — IPv4 firewall

Both chains end in a **drop**: anything not explicitly allowed is refused. Paste each step in Safe Mode, in the order shown. Order matters, because the first matching rule wins.

### Step 10 — IPv4 input (traffic to the router itself)

```routeros
/ip firewall filter
add action=accept chain=input connection-state=established,related \
    comment="INPUT: Accept established/related"
add action=drop chain=input connection-state=invalid \
    comment="INPUT: Drop invalid"
add action=accept chain=input protocol=icmp \
    comment="INPUT: Accept ICMP"
add action=accept chain=input in-interface-list=LAN \
    comment="INPUT: HOME management (Winbox/SSH still limited by /ip service)"
add action=accept chain=input dst-port=67 in-interface-list=DMZ protocol=udp \
    comment="INPUT: DHCP from DMZ"
add action=accept chain=input dst-port=53 in-interface-list=DMZ protocol=udp \
    comment="INPUT: DNS from DMZ (UDP)"
add action=accept chain=input dst-port=53 in-interface-list=DMZ protocol=tcp \
    comment="INPUT: DNS from DMZ (TCP)"
add action=accept chain=input dst-port=123 in-interface-list=DMZ protocol=udp \
    comment="INPUT: NTP from DMZ"
add action=accept chain=input dst-port=67 in-interface-list=CAM protocol=udp \
    comment="INPUT: DHCP from CAM"
add action=accept chain=input dst-port=123 in-interface-list=CAM protocol=udp \
    comment="INPUT: NTP from CAM"
add action=drop chain=input \
    comment="INPUT: Drop everything else"
```

**Why each rule exists**

| Rule | Reason |
|---|---|
| Established/related | Replies to connections the router opened itself (DNS upstream, NTP, DDNS) come back without needing their own rules. |
| Drop invalid | Packets that do not belong to any valid connection state (out-of-window TCP, stray replies). Hard to produce on purpose; kept as standard hygiene. |
| Accept ICMP | Ping and, more importantly, the error messages that path MTU discovery depends on. Every zone can ping the router. |
| HOME management | HOME is the only zone allowed to manage the router. Winbox and SSH are still restricted by the `address=` in `/ip service` (Step 6). |
| DMZ: DHCP, DNS UDP/TCP, NTP | The only router services the Pi needs. DNS over TCP covers answers too large for UDP. Everything else from the Pi, including SSH and Winbox, falls to the final drop. If the Pi is ever compromised from the VPN, it cannot be used to attack the router. |
| CAM: DHCP, NTP | Cameras get an address and the time. They get no DNS (they have no internet to resolve names for). |
| Drop everything else | Default deny: WAN, plus anything the rules above did not allow. |

The DHCP server reads its packets before the firewall, so DHCP keeps working even if those accept rules were missing. They are still listed so that the ruleset documents what each zone may use.

**Test**

| Where | Command | Expected |
|---|---|---|
| HOME computer | Winbox / `ssh admin@192.168.89.1` | ✅ |
| Pi | `ping -c2 192.168.91.1` | ✅ |
| Pi | `dig @192.168.91.1 example.com +short` and `dig +tcp @192.168.91.1 example.com +short` | ✅ both answer |
| Pi | `ntpdate -q 192.168.91.1` | ✅ |
| Pi | `ssh admin@192.168.91.1` | ❌ timeout |
| Pi | `ssh admin@192.168.89.1` | ❌ timeout (still traffic *to the router*, so it is in `input`) |

### Step 11 — IPv4 forward (traffic through the router)

```routeros
/ip firewall filter
add action=fasttrack-connection chain=forward connection-state=established,related \
    hw-offload=yes comment="FORWARD: Fasttrack established IPv4"
add action=accept chain=forward connection-state=established,related \
    comment="FORWARD: Accept established/related"
add action=drop chain=forward connection-state=invalid \
    comment="FORWARD: Drop invalid"
add action=accept chain=forward in-interface-list=LAN out-interface-list=WAN \
    comment="FORWARD: HOME -> Internet"
add action=accept chain=forward in-interface-list=DMZ out-interface-list=WAN \
    src-address-list=rpi comment="FORWARD: Rpi -> Internet (updates)"
add action=accept chain=forward dst-port=554 in-interface-list=DMZ \
    out-interface-list=CAM protocol=tcp src-address-list=rpi \
    comment="FORWARD: Rpi -> cameras (RTSP over TCP)"
add action=accept chain=forward in-interface-list=CAM out-interface-list=WAN \
    src-address-list=cam-internet-temp \
    comment="FORWARD: Temporary camera internet (firmware updates)"
add action=drop chain=forward in-interface-list=CAM out-interface-list=WAN \
    log=yes log-prefix=cam-wan-drop \
    comment="FORWARD: Cameras have no internet"
add action=accept chain=forward dst-port=80,443 in-interface-list=LAN \
    out-interface-list=CAM protocol=tcp src-address-list=cam-admin-temp \
    comment="FORWARD: Temporary HOME -> cameras web UI"
add action=drop chain=forward \
    comment="FORWARD: Drop everything else"
```

**Why each rule exists**

| Rule | Reason |
|---|---|
| Fasttrack (`hw-offload=yes`) | Once a connection is allowed, its packets skip the rest of the firewall. This matters because camera RTSP crosses zones (CAM → DMZ), so every packet is routed by the CPU rather than switched in hardware. The policy does not change: only established connections are fasttracked, and the first packet still went through every rule. `hw-offload=yes` only helps if the switch chip has L3 hardware offload and it is enabled (`/interface ethernet switch print`); otherwise fasttrack runs in software. Offloaded flows do not show up in rule counters or in `monitor-traffic`. |
| Established/related, drop invalid | Same reasoning as in `input`. |
| HOME → Internet | Normal browsing. |
| Pi → Internet | Package updates. Limited to the Pi's address, so another device plugged into `ether4` gets nothing. |
| Pi → cameras, TCP 554 | MediaMTX pulls the RTSP streams. Only the Pi, only RTSP, only over TCP (MediaMTX must use `rtspTransport: tcp`). The cameras' web UI, Telnet and vendor ports stay closed to the Pi. |
| Temporary camera internet | For a firmware update: add the camera to `cam-internet-temp` with a timeout. |
| Cameras have no internet | Many cameras "phone home" to vendor P2P clouds. With `log=yes`, every attempt is recorded with the `cam-wan-drop` prefix. |
| Temporary HOME → camera web UI | For setting up a camera from a HOME computer, with a timeout. |
| Drop everything else | Closes HOME → DMZ (the Pi is managed only through the VPN), DMZ → HOME, CAM → anything else, and WAN → inside. |

**Test**

| Where | Command | Expected |
|---|---|---|
| HOME computer | `curl -4 -m5 https://example.com -o /dev/null -w '%{http_code}\n'` | ✅ 200 |
| HOME computer | `ssh <user>@192.168.91.2` | ❌ timeout |
| Pi | `curl -4 -m5 https://deb.debian.org -o /dev/null -w '%{http_code}\n'` | ✅ |
| Pi | `ping -c2 -W2 <a HOME computer's IP>` | ❌ |

**Test the CAM zone with a laptop** (before connecting the real cameras). Plug a laptop into `ether5`:

| Where | Command | Expected |
|---|---|---|
| Laptop on CAM | macOS: `ipconfig getifaddr en0`; Linux: `ip -4 addr show eth0` | `192.168.90.1xx` |
| Laptop on CAM | `ping -c2 192.168.90.1` | ✅ |
| Laptop on CAM | `ping -c2 8.8.8.8` | ❌ 100% packet loss |
| Router | `/log print where message~"cam-wan-drop"` | the laptop's attempts |
| Router | `/ip firewall address-list add list=cam-internet-temp address=<laptop-ip> timeout=5m` and ping again from the laptop | ✅, and ❌ again after 5 min |
| Laptop on CAM | `sudo python3 -m http.server 554` | — |
| Pi | `curl -m3 http://192.168.90.1xx:554/` | ✅ answers (only 554 is open) |
| Laptop on CAM | `sudo python3 -m http.server 80` | — |
| Pi | `curl -m3 http://192.168.90.1xx/` | ❌ timeout |
| HOME computer | `curl -m3 http://192.168.90.1xx/` | ❌; ✅ after `/ip firewall address-list add list=cam-admin-temp address=<home-ip> timeout=5m` |

Unplug the laptop afterwards.

### Step 12 — Connect the cameras

Now the CAM zone is closed, so the cameras can be connected. Turn on the PoE switch on `ether5` and wait for the dynamic leases (30 seconds to 1 minute; repeat the `print` until every camera shows up).

Give each camera a fixed lease, so that the MediaMTX source URLs never change. Identify each camera by the MAC on its label:

```routeros
/ip dhcp-server lease print where server=dhcp-cam
/ip dhcp-server lease make-static [find server=dhcp-cam]
/ip dhcp-server lease set [find mac-address=<CAM1-MAC>] address=192.168.90.10 comment="Cam 1"
/ip dhcp-server lease set [find mac-address=<CAM2-MAC>] address=192.168.90.11 comment="Cam 2"
/ip dhcp-server lease set [find mac-address=<CAM3-MAC>] address=192.168.90.12 comment="Cam 3"
/ip dhcp-server lease set [find mac-address=<CAM4-MAC>] address=192.168.90.13 comment="Cam 4"
```

The cameras keep their dynamic address until they renew the lease. Power-cycle the PoE switch so that they request a new one right away.

**Test**

```routeros
/ip dhcp-server lease print where server=dhcp-cam ; # 192.168.90.10-.13, status=bound
/log print where message~"cam-wan-drop" ; # phone-home attempts, if any
```

```bash
# On the Pi
ffprobe -rtsp_transport tcp "rtsp://<user>:<pass>@192.168.90.10:554/<stream-path>"
```

Set each camera's NTP server to `192.168.90.1` and turn off its cloud/P2P features. Save checkpoint `part3`.

---

## Part 4 — IPv6 inside the router

The ISP only hands the router a single IPv6 address, with no prefix to delegate to the inside. Every internal zone therefore uses a ULA (`fd00::/8`, private IPv6) prefix, and NAT66 gives HOME and the VPN admins internet access. IPv6 on the WAN stays off until the firewall is ready (Part 6).

### Step 13 — WireGuard interface and the ULAs

```routeros
/interface wireguard add listen-port=14079 mtu=1280 name=wireguard-ipv6
/interface list member add interface=wireguard-ipv6 list=VPN

/ipv6 address
add address=fd00:10::1/64 advertise=no interface=wireguard-ipv6 \
    comment="ULA: VPN gateway and tunnel DNS (iDns)"
add address=fd00:20::1/64 advertise=no interface=ether4 \
    comment="ULA: DMZ (Rpi at fd00:20::cafe)"
add address=fd00:30::1/64 interface=bridge \
    comment="ULA: HOME (NAT66 egress, no prefix delegation from ISP)"
```

- The tunnel MTU is **1280**, the IPv6 minimum. The common default of 1420 assumes a 1500-byte path, which some ISPs and mobile carriers do not provide; when the path is smaller, the tunnel connects but traffic stalls. Every IPv6 path must carry 1280-byte packets, so this value works everywhere.
- The router generates its WireGuard key pair when the interface is created. The public key goes into every QR code (Part 9).
- `fd00:30::/64` is advertised (`advertise=yes` is the default), so HOME devices build an address by SLAAC. `fd00:20::/64` is not advertised, because the Pi uses a fixed address.

**Test**

```routeros
/interface wireguard print ; # running, listen-port=14079, public-key present
/ipv6 address print where !link-local
```

### Step 14 — Router advertisements

```routeros
/ipv6 nd set [find default=yes] disabled=yes
/ipv6 nd add interface=bridge dns=fd00:30::1 hop-limit=64 reachable-time=5m
/ipv6 nd add interface=ether4 dns=fd00:20::1 hop-limit=64 reachable-time=5m
```

The default entry would send router advertisements on **every** interface, including the tunnel, the WAN and CAM. It is disabled and replaced by explicit entries for HOME and DMZ only:

- **HOME:** announces the router as the default gateway, the `fd00:30::/64` prefix for SLAAC, and `fd00:30::1` as the DNS server. The ULA is used for DNS instead of the bridge's link-local because it is fixed by configuration and is the same on every operating system, while a link-local DNS server needs an interface scope that some operating systems handle poorly.
- **DMZ:** announces the router as the default gateway and `fd00:20::1` as DNS, with no prefix.

The comment of an `/ipv6 nd` entry is not shown by `print` or `export`. Read it with `:put [/ipv6 nd get <n> comment]`.

**Test** (on the HOME computer)

```bash
# macOS
ifconfig en0 | grep 'inet6 fd00:30'        # SLAAC address
netstat -rn -f inet6 | grep default        # default route via fe80::...
scutil --dns | grep fd00:30::1             # DNS from the RA
ping6 -c2 fd00:30::1

# Linux
ip -6 addr show eth0 | grep fd00:30        # SLAAC address
ip -6 route show default                   # default via fe80::... dev eth0
resolvectl dns eth0                        # includes fd00:30::1 (systemd-resolved)
ping -6 -c2 fd00:30::1
```

### Step 15 — Fixed IPv6 address on the Pi

The app and its network security config contain the literal address `fd00:20::cafe`. The Pi must always have it.

```bash
# On the Pi (NetworkManager)
nmcli -f NAME,DEVICE con show
CON="<eth0 connection name>"
sudo nmcli con mod "$CON" ipv6.method manual \
    ipv6.addresses fd00:20::cafe/64 ipv6.gateway fd00:20::1 ipv6.dns fd00:20::1
sudo nmcli con up "$CON"
```

**Test**

```bash
# On the Pi
ip -6 addr show eth0 | grep fd00:20::cafe
ping6 -c2 fd00:20::1
```

Save checkpoint `part4`.

---

## Part 5 — IPv6 firewall

IPv6 has no NAT protecting the inside by default, and in this design it carries the VPN. Its firewall therefore does most of the work. It is built in four steps: address lists, anti-spoofing, `input`, `forward`.

### Step 16 — Address lists and anti-spoofing (raw)

```routeros
/ipv6 firewall address-list
add address=fd00:20::cafe/128 list=cameras.casa comment="Rpi (MediaMTX, Samba)"
add address=fd00:10::a:0/112 list=vpn-admin comment="VPN admins: fd00:10::a:N"
add address=fd00:10::c:0/112 list=vpn-clients comment="Documentation only: clients fd00:10::c:N"

/ipv6 firewall raw
add action=drop chain=prerouting in-interface=!wireguard-ipv6 \
    src-address=fd00:10::/64 comment="RAW: VPN ULA source only from the tunnel"
add action=drop chain=prerouting in-interface=!ether4 \
    src-address=fd00:20::/64 comment="RAW: DMZ ULA source only from ether4"
add action=drop chain=prerouting in-interface=!bridge \
    src-address=fd00:30::/64 comment="RAW: HOME ULA source only from the bridge"
```

**Why**

- **`vpn-admin`** is an *allow list*: addresses in `fd00:10::a:0/112` (`fd00:10::a:1`, `::a:2`, …) get admin rights, and every other VPN address gets only what the app needs. The `/112` ends exactly at a group boundary, so each profile is one list entry and the profile can be read from the address itself: `a` for admin, `c` for client. See Part 9.
- **`vpn-clients`** is not referenced by any rule. It only documents the client block (`fd00:10::c:0/112`); the allow-list model already gives the minimum to anyone outside `vpn-admin`.
- **Raw anti-spoofing.** Every IPv6 rule that follows trusts the *source address* (`vpn-admin`, `cameras.casa`). Inside the tunnel, the source is trustworthy: WireGuard only accepts from a peer packets whose source is in that peer's `allowed-address`. Outside the tunnel nothing guarantees it. Without these rules, a host on the ISP router's network could send a packet with an admin source address toward the router. The `raw` table drops such packets before connection tracking. Only the project's three prefixes are dropped, not all of `fd00::/8`, because many ISP routers use a ULA of their own on the WAN side.

**Test:** spoofing cannot be tested without extra tooling. Instead, check that legitimate traffic is not hit:

```routeros
/ipv6 firewall raw print stats
# All counters at (or very near) 0 after the HOME and Pi tests below
```

### Step 17 — IPv6 input

```routeros
/ipv6 firewall filter
add action=drop chain=input in-interface-list=CAM \
    comment="INPUT: No IPv6 from CAM"
add action=accept chain=input connection-state=established,related \
    comment="INPUT: Accept established/related"
add action=drop chain=input connection-state=invalid \
    comment="INPUT: Drop invalid"
add action=accept chain=input dst-port=22 in-interface=wireguard-ipv6 \
    protocol=tcp src-address-list=vpn-admin \
    comment="INPUT: SSH to router from VPN admins"
add action=drop chain=input icmp-options=134:0-255 in-interface-list=!WAN \
    protocol=icmpv6 comment="INPUT: Router Advertisements only from WAN"
add action=accept chain=input protocol=icmpv6 \
    comment="INPUT: Accept ICMPv6 (ND, PMTUD, ping)"
add action=accept chain=input dst-port=546 in-interface-list=WAN protocol=udp \
    src-address=fe80::/10 src-port=547 comment="INPUT: DHCPv6 client"
add action=accept chain=input dst-port=14079 in-interface-list=WAN protocol=udp \
    comment="INPUT: WireGuard handshake"
add action=accept chain=input dst-port=53 protocol=udp \
    src-address-list=cameras.casa comment="INPUT: Rpi DNS (UDP)"
add action=drop chain=input src-address-list=cameras.casa \
    comment="INPUT: Rpi does not manage the router"
add action=accept chain=input in-interface-list=LAN \
    comment="INPUT: Accept management from LAN"
add action=accept chain=input dst-port=53 in-interface=wireguard-ipv6 \
    protocol=udp comment="INPUT: DNS from VPN (UDP)"
add action=accept chain=input dst-port=53 in-interface=wireguard-ipv6 \
    protocol=tcp comment="INPUT: DNS from VPN (TCP)"
add action=drop chain=input in-interface-list=WAN \
    comment="INPUT: Drop everything else from WAN"
add action=drop chain=input \
    comment="INPUT: Drop everything else"
```

**Why each rule exists**

| Rule | Reason |
|---|---|
| No IPv6 from CAM (first) | The cameras get no IPv6 address, but every IPv6 device has a link-local address and can still talk to the router with it. This drop comes before everything else, even established connections, so cameras cannot use IPv6 for anything. |
| Established/related, invalid | Same as IPv4. |
| SSH from VPN admins | Admins manage the router through the tunnel. The rule needs both the tunnel interface and the `vpn-admin` source, so a spoofed source from another interface does not match even without the raw rules. |
| RA only from WAN | A router advertisement can change the router's own default route. Only the ISP may send them, on `ether1`. Any device in HOME, DMZ, CAM or the tunnel could otherwise announce itself as the gateway. Testing it requires an RA forging tool. |
| Accept ICMPv6 | Unlike IPv4, IPv6 does not work without ICMPv6: neighbor discovery replaces ARP, and "packet too big" messages drive path MTU discovery. Ping to the router is also allowed. |
| DHCPv6 client | Replies from the ISP's DHCPv6 server (UDP 547 → 546, from link-local) on the WAN only. Without the interface and source port, any HOME device could send forged DHCPv6 replies to the router. Tested indirectly in Step 20 (`status=bound`). |
| WireGuard handshake | The only port open to the internet. WireGuard does not reply to unauthenticated packets, so a port scan sees nothing. No `log`: the log would only fill with scanner noise. |
| Rpi DNS (UDP), then Rpi drop | The Pi only uses the router for DNS over IPv6. The drop prevents it from reaching SSH, Winbox or anything else on the router through IPv6. The rules have no interface because the raw rule of Step 16 already guarantees that `fd00:20::cafe` can only come from `ether4`. They must come **before** the LAN accept below. |
| Accept management from LAN | HOME devices reach the router (ping, DNS). SSH and Winbox still refuse `fd00:30::/64`, because it is not in the `address=` of `/ip service`. |
| DNS from VPN (UDP/TCP) | The QR code sets `iDns = fd00:10::1`, so every peer uses the router as its resolver. |
| Drop from WAN, then drop everything | The WAN drop is redundant with the final one; it only keeps a separate counter for internet noise. |

**Test**

| Where | Command | Expected |
|---|---|---|
| HOME computer | `ping6 -c2 fd00:30::1` (Linux: `ping -6 -c2 fd00:30::1`) | ✅ |
| HOME computer | `dig @fd00:30::1 example.com +short` | ✅ |
| Pi | `dig @fd00:20::1 example.com +short` | ✅ |
| Pi | `ping6 -c2 fd00:20::1` | ✅ (ICMPv6 comes before the Rpi drop) |
| Pi | `ssh -6 admin@fd00:20::1` | ❌ timeout |
| Laptop on CAM (as in Step 11) | macOS: `ping6 -c2 ff02::1%en0`; Linux: `ping -6 -c2 ff02::1%eth0`. The router's link-local must not answer | ❌ no reply from the router |

The VPN rules (SSH and DNS from the tunnel) are tested in Step 33.

### Step 18 — IPv6 forward

```routeros
/ipv6 firewall filter
add action=drop chain=forward in-interface-list=CAM \
    comment="FORWARD: No IPv6 from CAM"
add action=drop chain=forward out-interface-list=CAM \
    comment="FORWARD: No IPv6 to CAM"
add action=accept chain=forward connection-state=established,related \
    comment="FORWARD: Accept established/related"
add action=drop chain=forward connection-state=new out-interface=wireguard-ipv6 \
    comment="FORWARD: Nothing initiates toward VPN peers (incl. peer-to-peer)"
add action=drop chain=forward connection-state=invalid \
    comment="FORWARD: Drop invalid"
add action=accept chain=forward icmp-options=1:0-255 protocol=icmpv6 \
    comment="FORWARD: ICMPv6 destination unreachable"
add action=accept chain=forward icmp-options=2:0-255 protocol=icmpv6 \
    comment="FORWARD: ICMPv6 packet too big (PMTUD)"
add action=accept chain=forward icmp-options=3:0-255 protocol=icmpv6 \
    comment="FORWARD: ICMPv6 time exceeded"
add action=accept chain=forward icmp-options=4:0-255 protocol=icmpv6 \
    comment="FORWARD: ICMPv6 parameter problem"
add action=accept chain=forward dst-address-list=cameras.casa icmp-options=128:0 \
    in-interface=wireguard-ipv6 protocol=icmpv6 \
    comment="FORWARD: VPN ping -> Rpi"
add action=accept chain=forward dst-address-list=cameras.casa dst-port=8889 \
    in-interface=wireguard-ipv6 protocol=tcp \
    comment="FORWARD: VPN -> Rpi (WHEP signaling)"
add action=accept chain=forward dst-address-list=cameras.casa dst-port=8189 \
    in-interface=wireguard-ipv6 protocol=udp \
    comment="FORWARD: VPN -> Rpi (WebRTC ICE/UDP)"
add action=accept chain=forward in-interface=wireguard-ipv6 \
    out-interface-list=WAN src-address-list=vpn-admin \
    comment="FORWARD: VPN admin -> Internet (NAT66)"
add action=accept chain=forward dst-address-list=cameras.casa \
    dst-port=22,80,443,445,8888 in-interface=wireguard-ipv6 protocol=tcp \
    src-address-list=vpn-admin comment="FORWARD: VPN admin -> Rpi (SSH, web, SMB, HLS)"
add action=drop chain=forward in-interface=wireguard-ipv6 \
    comment="FORWARD: Drop everything else from VPN"
add action=accept chain=forward in-interface-list=LAN out-interface-list=WAN \
    comment="FORWARD: LAN -> WAN"
add action=drop chain=forward connection-state=new in-interface-list=WAN \
    comment="FORWARD: Drop unsolicited from WAN"
add action=drop chain=forward connection-state=new in-interface-list=DMZ \
    comment="FORWARD: Rpi initiates nothing over IPv6"
add action=drop chain=forward \
    comment="FORWARD: Drop everything else"
```

**Why each rule exists**

| Rule | Reason |
|---|---|
| No IPv6 from/to CAM | Same idea as in `input`: the cameras are IPv4 only. These drops come before established/related, so not even a reply can cross. |
| Nothing initiates toward VPN peers | No zone may open a connection to a VPN peer, and a peer may not open one to another peer. This protects clients' phones from each other and from the Pi. Replies to connections the peer opened are already accepted by the rule above it. Testing peer-to-peer needs two connected peers (Step 33). |
| Invalid | Same as `input`. |
| ICMPv6 errors (types 1–4) | Errors that belong to a tracked connection already pass as `related`, and errors that do not are `invalid`. These four rules rarely match, and they are kept as an explicit statement that IPv6 error messages must never be blocked. Check their counters (`print stats`) over time; if they stay at zero, they can be removed. |
| VPN ping → Pi | Diagnostics: any peer can check that the Pi is reachable. |
| VPN → Pi, TCP 8889 and UDP 8189 | What the app needs: WHEP signaling (HTTP) and WebRTC media (ICE over UDP). Every peer, admin or client. |
| VPN admin → Internet | Admins can route all their traffic through home. Clients cannot: their traffic to the internet is dropped. |
| VPN admin → Pi, admin ports | SSH, web, Samba (recordings) and HLS. Admins only. |
| Drop everything else from VPN | Closes everything else for the tunnel: HOME, CAM, the rest of the DMZ. |
| LAN → WAN | IPv6 internet for HOME (through NAT66, Step 19). |
| Drop unsolicited from WAN | Nothing from the internet may open a connection inside. Redundant with the final drop; kept for a separate counter. |
| Rpi initiates nothing over IPv6 | The Pi only *answers* over IPv6. It has no IPv6 internet path anyway (no NAT66 for the DMZ); this rule makes the intent explicit. Its IPv4 updates are handled in Step 11. |
| Drop everything else | Default deny (HOME → DMZ over IPv6, for example). |

**Test**

| Where | Command | Expected |
|---|---|---|
| HOME computer | `ping6 -c2 fd00:20::cafe` (Linux: `ping -6`) | ❌ (the Pi is reachable only through the VPN) |
| Pi | `ping6 -c2 fd00:30::1` | ✅ (to the router, `input`) |
| Pi | `ping6 -c2 <a HOME device's fd00:30:: address>` | ❌ |

### Step 19 — MSS clamp and NAT66

```routeros
/ipv6 firewall mangle
add action=change-mss chain=forward new-mss=clamp-to-pmtu protocol=tcp tcp-flags=syn \
    comment="Clamp MSS to path MTU (covers WireGuard 1280)"

/ipv6 firewall nat
add action=masquerade chain=srcnat in-interface=bridge out-interface=ether1 \
    comment="NAT66: HOME -> Internet (no prefix delegation from ISP)"
add action=masquerade chain=srcnat in-interface=wireguard-ipv6 out-interface=ether1 \
    src-address-list=vpn-admin comment="NAT66: VPN admin -> Internet"
```

**Why**

- **MSS clamp.** When a TCP connection starts, it announces its maximum segment size based on the local MTU. Across the 1280-byte tunnel, a 1500-based MSS would produce packets that are too large. If ICMPv6 "packet too big" is lost on the way, the connection hangs after the handshake. Rewriting the MSS in SYN packets avoids depending on PMTUD. The router computes 1220 for the tunnel.
- **NAT66.** The ISP gives the router a single `/128` and no prefix, so internal ULAs have nowhere to be routed. Masquerade gives HOME and the VPN admins IPv6 internet through the router's own address. There is no NAT between the VPN and the Pi: the Pi sees each peer's real address, so its own firewall (ufw, Part 8) can apply the same admin/client split.

These rules can only be tested once the WAN has IPv6 (Step 20) and peers exist (Step 33).

Save checkpoint `part5`.

---

## Part 6 — IPv6 on the WAN

### Step 20 — DHCPv6 client and router advertisements on ether1

Only now, with the IPv6 firewall in place, does the router get a public IPv6 address.

```routeros
/ipv6 settings set accept-redirects=no accept-router-advertisements=yes \
    max-neighbor-entries=8192
/ipv6 dhcp-client add interface=ether1 request=address pool-name=pool-isp \
    add-default-route=yes use-peer-dns=no
```

RouterOS prints a notice that a **reboot is required** for the new IPv6 settings to take effect. Reboot before testing:

```routeros
/system reboot
```

After the reboot, the ISP may hand `ether1` a different IPv6 address. If the ISP router's firewall has an inbound rule for UDP 14079, it points to a specific address: check that it matches the address printed in the test below, and update it whenever that address changes. A rule pointing to an old address leaves the VPN unreachable even though everything on the MikroTik looks correct.

- `accept-router-advertisements=yes` is needed because a router with IPv6 forwarding enabled ignores RAs by default. The ISP's RA provides the default route. The firewall still drops RAs coming from anywhere but the WAN (Step 17).
- `accept-redirects=no`: an ICMPv6 redirect could change the router's routing table.
- `request=address`: this ISP provides an address, not a prefix. `pool-name` is unused with `request=address`, but ready if the ISP ever starts delegating a prefix.

**Test**

```routeros
/ipv6 dhcp-client print ; # status=bound
/ipv6 address print where interface=ether1 ; # global address(es)
/ipv6 route print where dst-address=::/0 ; # active default route
/ping 2001:4860:4860::8888 count=3
```

```bash
# On the HOME computer
curl -6 -m5 https://ipv6.icanhazip.com        # the router's ether1 address (NAT66)
```

A HOME device that has only a ULA prefers IPv4 for dual-stack destinations (RFC 6724). This is expected: NAT66 serves IPv6-only destinations and the VPN connection from inside the home.

**From outside** (a laptop tethered to a phone on mobile data, with IPv6):

| Command | Expected |
|---|---|
| `ping6 -c2 <ether1 IPv6>` (Linux: `ping -6`) | ✅ (ICMPv6 is accepted) |
| `nc -6 -vz -w3 <ether1 IPv6> 22` | ❌ timeout |

If the ping fails, check the ISP router's IPv6 firewall: it must let inbound traffic reach the MikroTik, at least UDP 14079. The final proof is the VPN handshake in Step 33.

Save checkpoint `part6`.

---

## Part 7 — DNS name and DDNS

### Step 21 — Local name for the Pi

```routeros
/ip dns static add address=fd00:20::cafe name=<site>.cameras.casa ttl=3m type=AAAA
```

A convenience name for admins (SSH, Samba, browser), resolved by the router for HOME, DMZ and the VPN. The app does not use it: it uses the literal `[fd00:20::cafe]`.

This is a split-horizon record: it only exists for devices that use the router as their resolver. If you do not control the public zone of the domain you use, prefer a name under `home.arpa` (RFC 8375), so that nobody else can answer for it outside the tunnel.

**Test** (on the HOME computer)

```bash
dig AAAA <site>.cameras.casa @192.168.89.1 +short    # fd00:20::cafe
```

### Step 22 — DDNS for the VPN endpoint

The router's IPv6 address can change when the ISP renews it. A script publishes the current address as an AAAA record on Cloudflare every 15 minutes, and only calls the API when the address actually changed.

**a) Trust store.** The script validates Cloudflare's TLS certificate (`check-certificate=yes`). This setup does **not** trust RouterOS's built-in CA bundle; it trusts only the root that signs `api.cloudflare.com`.

1. **Find the root CA.** The server sends its own certificate and the intermediates, but not the root. The issuer (`i:`) of the **last** certificate in the chain is the root you need:

   ```bash
   # On the HOME computer
   openssl s_client -connect api.cloudflare.com:443 -servername api.cloudflare.com \
       -showcerts </dev/null 2>/dev/null | grep -E '^ *[0-9]+ s:|i:'
   # Example of the last lines (the names change when Cloudflare changes CA):
   #  2 s:C = BE, O = GlobalSign nv-sa, OU = Root CA, CN = GlobalSign Root CA
   #    i:C = BE, O = GlobalSign nv-sa, OU = Root CA, CN = GlobalSign Root CA
   ```

   Note the `CN` of that last issuer (`GlobalSign Root CA` in the example).

2. **Export it from the computer's own trust store** as `root-ca.pem`. The operating system already ships the root CAs, so nothing has to be downloaded:

   ```bash
   # macOS
   security find-certificate -c "GlobalSign Root CA" -p \
       /System/Library/Keychains/SystemRootCertificates.keychain > root-ca.pem

   # Linux (Debian/Ubuntu: one file per root CA, spaces become underscores)
   cp /etc/ssl/certs/GlobalSign_Root_CA.pem root-ca.pem

   # Both: check that it is the right certificate and still valid
   openssl x509 -in root-ca.pem -noout -subject -enddate
   ```

3. **Copy it to the router:**

   ```bash
   # On the HOME computer
   scp ./root-ca.pem admin@192.168.89.1:~/
   ```

4. **Import it, trust only it, and test:**

```routeros
/certificate import file-name=root-ca.pem passphrase=""
/certificate print ; # the imported CA must show the "T" (trusted) flag
/certificate settings set builtin-trust-anchors=not-trusted
/tool fetch url="https://api.cloudflare.com/client/v4/" check-certificate=yes output=none
# Expected: "status 400" (an HTTP answer after TLS validated), not a certificate error
```

> **Trade-off.** Trusting one root narrows who can impersonate Cloudflare, but if Cloudflare switches to another CA, DDNS fails until you import the new root. If that maintenance is not worth it, keep `builtin-trust-anchors=trusted`. Never use `check-certificate=no`: the script sends the API token in a header.

**b) The script.** Save the block below as `ddns.rsc` on the HOME computer and fill in the four values. The file only carries the commands: `/import` runs them, and the script is created with the name given by `name=` (`update-ddns-cloudflare`), whatever the file is called. The scheduler calls the script by that same name.
```routeros
/system script add name=update-ddns-cloudflare owner=admin \
    policy=read,write,policy,test dont-require-permissions=no source={
# Update an AAAA record on Cloudflare with the current WAN IPv6 (DDNS)
:local cfToken "<API-TOKEN>"
:local cfZoneId "<ZONE-ID>"
:local cfRecordId "<RECORD-ID>"
:local domainName "<vpn-hostname>"
:local wanInterface "ether1"

# 1. Current global IPv6 on the WAN
:local ipList [/ipv6 address find interface=$wanInterface global=yes]
:if ([:len $ipList] = 0) do={
    :log error "DDNS: no global IPv6 address on $wanInterface"
    :return
}
:local currentIP [/ipv6 address get ($ipList->0) address]
:local slashPos [:find $currentIP "/"]
:if ($slashPos >= 0) do={ :set currentIP [:pick $currentIP 0 $slashPos] }
:set currentIP [:tostr [:toip6 $currentIP]]

# 2. What the record currently resolves to
:local dnsIP ""
:local dnsResolveFailed false
:do {
    :set dnsIP [:tostr [:toip6 [:resolve $domainName type=ipv6]]]
} on-error={
    :log warning "DDNS: could not resolve $domainName"
    :set dnsResolveFailed true
}

# 3. Update only when needed ("proxied": false is required for UDP/VPN)
:if ($dnsResolveFailed || $currentIP != $dnsIP) do={
    :local payload "{\"type\":\"AAAA\",\"name\":\""
    :set payload ($payload . $domainName . "\",\"content\":\"" . $currentIP)
    :set payload ($payload . "\",\"ttl\":120,\"proxied\":false}")
    :local url "https://api.cloudflare.com/client/v4/zones/$cfZoneId/dns_records/$cfRecordId"
    :do {
        :local result [/tool fetch mode=https http-method=put url=$url \
            http-header-field="Authorization: Bearer $cfToken, Content-Type: application/json" \
            check-certificate=yes http-data=$payload output=user as-value]
        :if ([:find ($result->"data") "\"success\":true"] < 0) do={
            :log error ("DDNS: Cloudflare error: " . ($result->"data"))
        }
    } on-error={
        :log error "DDNS: request to Cloudflare failed (token, IDs or connectivity)"
    }
}
}

/system scheduler add name=update-ddns-cloudflare interval=15m start-time=startup \
    policy=read,write,policy,test on-event="/system script run update-ddns-cloudflare"
```

Copy it to the router and import it:

```bash
# On the HOME computer
scp ./ddns.rsc admin@192.168.89.1:~/
```

```routeros
/import ddns.rsc
/system script print where name=update-ddns-cloudflare ; # the script exists
/file remove ddns.rsc
```

Delete `ddns.rsc` from the HOME computer as well: it contains the token.

**Test**

```routeros
/system script run update-ddns-cloudflare
/log print where message~"DDNS" ; # no errors
/file print where name~"ddns" ; # nothing left
```

```bash
# On the HOME computer
dig AAAA <vpn-hostname> @1.1.1.1 +short    # the router's ether1 address
```

Save checkpoint `part7`. The router is now complete, except for VPN peers.

---

## Part 8 — Raspberry Pi firewall (ufw)

The router already decides who reaches the Pi. The Pi's own firewall repeats the same policy as a **second layer**: if a router rule is ever wrong, or someone plugs another device into the DMZ, the Pi still only accepts what the app and the admins need. Since there is no NAT between the VPN and the DMZ (Step 19), the Pi sees each peer's real address and can tell admins from clients by itself.

This part assumes a Pi with no firewall configured yet.

### Step 23 — Install ufw and set the defaults

```bash
# On the Pi
sudo apt update && sudo apt install -y ufw
grep '^IPV6=' /etc/default/ufw     # must be IPV6=yes (the default)

# Nothing comes in, the Pi may reach out, and it is not a router
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw default deny routed
sudo ufw logging low
```

| Setting | Reason |
|---|---|
| `IPV6=yes` | Every rule in this part is IPv6. With `no`, ufw ignores them and IPv6 stays open |
| `deny incoming` | Anything not listed in Step 24 is dropped (silently, so a scan sees a timeout and not a refusal). This also covers the MediaMTX ports that must stay local: RTSP server (8554), API (9997), metrics (9998) |
| `allow outgoing` | The Pi starts its own connections: RTSP to the cameras, package updates over IPv4, DNS and NTP to the router. What it may reach is already limited by the router (Steps 11 and 18) |
| `deny routed` | The Pi does not forward traffic between interfaces. If IP forwarding were ever enabled by mistake, it still could not act as a bridge between the DMZ and anything else |
| `logging low` | Blocked packets are logged with rate limiting (`journalctl -k \| grep 'UFW BLOCK'`) |

ufw is installed **inactive**, so nothing is enforced yet.

**Test** (on the Pi)

```bash
sudo ufw status verbose
# Status: inactive (the defaults show once it is enabled in Step 25)
```

### Step 24 — Add the rules

```bash
# On the Pi
# Every VPN peer (the app): WHEP signaling and WebRTC media
sudo ufw allow from fd00:10::/64 to any port 8889 proto tcp comment 'VPN peers: WHEP signaling'
sudo ufw allow from fd00:10::/64 to any port 8189 proto udp comment 'VPN peers: WebRTC ICE'

# VPN admins only (same block as the router's vpn-admin list)
sudo ufw allow from fd00:10::a:0/112 to any port 22 proto tcp comment 'VPN admins: SSH'
sudo ufw allow from fd00:10::a:0/112 to any port 80 proto tcp comment 'VPN admins: web'
sudo ufw allow from fd00:10::a:0/112 to any port 445 proto tcp comment 'VPN admins: Samba'
sudo ufw allow from fd00:10::a:0/112 to any port 8888 proto tcp comment 'VPN admins: HLS'
```

**Why each rule exists**

| Rule | Reason |
|---|---|
| 8889/TCP and 8189/UDP from `fd00:10::/64` | What the app needs, for every peer. The whole `/64` matches the router, which also allows these ports to every peer |
| 22, 80, 445, 8888/TCP from `fd00:10::a:0/112` | SSH, web, Samba (recordings) and HLS for admins only. The block is the router's `vpn-admin` list. The router also allows 443 for admins; open it here only if some service on the Pi uses HTTPS |
| No IPv4 rules | Nothing needs to open a connection *to* the Pi over IPv4: the VPN is IPv6 only, and RTSP is started by the Pi |

ufw's built-in rules (`/etc/ufw/before.rules` and `before6.rules`) already accept established connections, DHCP replies, the ICMPv6 that IPv6 needs (neighbor discovery, "packet too big") and ping. That is why `ping6 fd00:20::cafe` keeps working for every peer, matching the router's `VPN ping -> Rpi` rule.

**Test** (on the Pi)

```bash
sudo ufw show added
# Exactly the 6 rules above, all with a fd00:10:: source
```

### Step 25 — Enable with a safety net

The admin manages the Pi over SSH **through the VPN** (HOME has no access to the Pi). If a rule is wrong, enabling ufw cuts that session. Schedule an automatic `ufw disable` first, the same idea as Safe Mode on the router:

```bash
# On the Pi
sudo systemd-run --unit=ufw-rollback --on-active=10m /usr/sbin/ufw disable
sudo ufw --force enable
```

Open a **new** SSH session to the Pi through the VPN as an admin, without closing the current one. If it works, cancel the rollback:

```bash
# On the Pi
sudo systemctl stop ufw-rollback.timer
systemctl list-timers ufw-rollback.timer    # 0 timers listed
```

If the new session fails, wait for the rollback (10 minutes), reconnect and fix the rules before enabling again.

**Test** (on the Pi)

```bash
sudo ufw status verbose
# Status: active
# Default: deny (incoming), allow (outgoing), deny (routed)
# 6 rules, all with a fd00:10:: source
systemctl is-enabled ufw                     # enabled: survives a reboot
```

### Step 26 — Test ufw on its own, from the router

The router's firewall blocks the same things as ufw, so a test through the VPN cannot show which of the two did the blocking. The router can test ufw alone: traffic **it** generates does not pass through its own `forward` rules, and it can pick a source address. `fd00:20::1` (the router on the DMZ) is outside both VPN blocks, and `fd00:10::1` (the router on the tunnel) is in `fd00:10::/64` but outside the admin block. A dropped request hangs; stop it with `Ctrl+C` after about 10 seconds.

```routeros
# Source fd00:20::1: not a VPN peer, so ufw drops everything (hangs until timeout)
/tool fetch url="http://[fd00:20::cafe]:8889/" src-address=fd00:20::1 output=none
# Source fd00:10::1: counts as a VPN peer. 8889 answers (any HTTP status, e.g. 404)
/tool fetch url="http://[fd00:20::cafe]:8889/" src-address=fd00:10::1 output=none
# Same source, admin port: not in fd00:10::a:0/112, so dropped (timeout)
/tool fetch url="http://[fd00:20::cafe]:8888/" src-address=fd00:10::1 output=none
# IPv4: no IPv4 rule, so dropped (timeout)
/tool fetch url="http://192.168.91.2:8889/" output=none
# Ping is always allowed
/ping fd00:20::cafe count=2
```

| Source | Port | Expected |
|---|---|---|
| `fd00:20::1` | 8889 | ❌ timeout |
| `fd00:10::1` | 8889 | ✅ HTTP answer (any status) |
| `fd00:10::1` | 8888 | ❌ timeout |
| `192.168.91.1` (IPv4) | 8889 | ❌ timeout |
| any | ping | ✅ |

On the Pi, the blocked attempts appear in the log:

```bash
# On the Pi
sudo journalctl -k --since "-5 min" | grep 'UFW BLOCK'
```

The admin side (`fd00:10::a:N` on 22, 80, 445 and 8888) is tested with a real admin peer in Step 33.

**Keeping ufw in sync with the router.** The two layers must use the same blocks: `fd00:10::/64` for the app ports and the router's `vpn-admin` (`fd00:10::a:0/112`) for the admin ports. If `vpn-admin` ever changes, for example to one `/128` per admin device, change both, in the same session.

---

## Part 9 — Generating VPN access

Each person or device gets its **own** WireGuard peer, its own `/128` address and its own QR code. The QR code holds a JSON document that the Android app reads at setup time.

### Step 27 — Address plan

| Block | Profile | Gets |
|---|---|---|
| `fd00:10::1` | Router | — |
| `fd00:10::a:0/112` → `fd00:10::a:N` | **Admin** (`vpn-admin`) | App + SSH/web/Samba/HLS on the Pi + SSH on the router + internet through the tunnel |
| `fd00:10::c:0/112` → `fd00:10::c:N` | **Client** (`vpn-clients`, documentation only) | App only: TCP 8889 and UDP 8189 to the Pi, DNS and ping |
| Anything else in `fd00:10::/64` | Not assigned | Same as client, if ever used |

The last group (`N`, `1`–`ffff` in hex) is just a counter, so each profile has room for 65,535 devices and needs no range arithmetic.

The profile is decided **only by the address**: a client issued `fd00:10::a:7` is an admin. The generator in Step 29 derives the block from the chosen profile and only asks for the index, so the block is never typed by hand. Keep a record of every address handed out. For an even stricter setup, keep only one `/128` per admin device in `vpn-admin`, at the cost of editing the list (and the Pi's ufw) for every new admin.

The Pi's ufw (Part 8) uses the same blocks: `fd00:10::/64` for ports 8889/TCP and 8189/UDP, and `fd00:10::a:0/112` for 22, 80, 445 and 8888/TCP.

### Step 28 — Collect the shared values

These go into every QR code (`vpnConfigDefaults`):

```routeros
:put [/interface wireguard get wireguard-ipv6 public-key] ; # pPuk
```

| Field | Value |
|---|---|
| `iDns` | `fd00:10::1` |
| `iMtu` | `1280` |
| `pPuk` | router public key (above) |
| `pAllowedips` | `::/0, 0.0.0.0/0` |
| `pEndpoint` | `<vpn-hostname>:14079` |
| `pPersistentKeepAlive` | `25` |

`::/0, 0.0.0.0/0` sends all of the device's traffic through the tunnel while the app is open. Clients have no internet through the tunnel, so with this value their phone loses internet while the app is in the foreground. To avoid that for clients, use a split tunnel: `"pAllowedips": "fd00:20::cafe/128, fd00:10::1/128"`.

### Step 29 — Generate keys and entries (interactive script)

Generate keys on the HOME computer, not on the router. The router only needs each peer's **public** key and the preshared key. A private key stored on the router ends up in every `export show-sensitive` and backup. (Creating a peer in Winbox with client config generation stores the private key on the router.)

The script asks for the profile, the number of peers and the first index, then writes the RouterOS commands and the `vpn.json` entries from the same data, so the router and the QR codes cannot disagree. Before asking anything, it shows the RouterOS commands that list the addresses already in use in each block, so that you pick a free index.

Save as `qr-code-gen/gen-peers.sh` on the HOME computer and fill in the two values at the top:

```bash
#!/usr/bin/env bash
# Interactive WireGuard peer generator: RouterOS commands + vpn.json entries
# for the QR generator (main.py). Admins: fd00:10::a:N, clients: fd00:10::c:N.
set -euo pipefail

SERVER_PUB="<router public key>"
ENDPOINT="<vpn-hostname>:14079"
ALLOWED="::/0, 0.0.0.0/0"
OUT=peers

cat <<'INFO'
------------------------------------------------------------------------------
INFO: check which addresses are already in use before choosing the first index.
Run on the MikroTik:

  Clients (fd00:10::c:N):
  /interface wireguard peers print proplist=name,allowed-address where allowed-address~"fd00:10::c:"

  Admins (fd00:10::a:N):
  /interface wireguard peers print proplist=name,allowed-address where allowed-address~"fd00:10::a:"
------------------------------------------------------------------------------
INFO

# 1. Profile -> block: fd00:10::a:0/112 (vpn-admin) or fd00:10::c:0/112 (clients)
while true; do
    read -r -p "Generate [c]lient or [a]dmin peers? " answer
    case "$answer" in
        c|client) PROFILE=client; BLOCK=c; break ;;
        a|admin)  PROFILE=admin;  BLOCK=a; break ;;
        *) echo "Answer c (client) or a (admin)." ;;
    esac
done

# 2. How many peers
while true; do
    read -r -p "How many peers? " COUNT
    if [[ "$COUNT" =~ ^[0-9]+$ ]] && [ "$COUNT" -ge 1 ]; then break; fi
    echo "Enter a positive number."
done

# 3. First index (hex, the N in fd00:10::${BLOCK}:N)
while true; do
    read -r -p "First address: fd00:10::${BLOCK}:" first_hex
    if [[ "$first_hex" =~ ^[0-9a-fA-F]{1,4}$ ]]; then
        FIRST=$((16#$first_hex))
        LAST=$((FIRST + COUNT - 1))
        if [ "$FIRST" -ge 1 ] && [ "$LAST" -le $((16#ffff)) ]; then break; fi
    fi
    echo "Enter a hex index so that the whole batch stays within 1-ffff."
done

# 4. Confirm before touching any file
printf '\nAbout to generate %d %s peer(s): fd00:10::%s:%x to fd00:10::%s:%x\n' \
    "$COUNT" "$PROFILE" "$BLOCK" "$FIRST" "$BLOCK" "$LAST"
if [ "$PROFILE" = admin ]; then
    echo "WARNING: admin peers get SSH, Samba and internet through the tunnel."
fi
read -r -p "Proceed? [y/N] " answer
[[ "$answer" =~ ^[yY]$ ]] || { echo "Cancelled."; exit 0; }

# 5. Generate
umask 077
mkdir -p "$OUT"
: > "$OUT/peers.rsc"
: > "$OUT/index.txt"
entries=()

for ((i = 0; i < COUNT; i++)); do
    index=$(printf '%x' $((FIRST + i)))
    addr="fd00:10::${BLOCK}:${index}/128"
    name="${PROFILE}-${BLOCK}-${index}"
    priv=$(wg genkey)
    pub=$(printf '%s' "$priv" | wg pubkey)
    psk=$(wg genpsk)

    printf '/interface wireguard peers add interface=wireguard-ipv6 name=%s public-key="%s" preshared-key="%s" allowed-address=%s\n' \
        "$name" "$pub" "$psk" "$addr" >> "$OUT/peers.rsc"
    printf 'qrcode-%s.png\t%s\n' "${addr%/128}" "$name" >> "$OUT/index.txt"

    entries+=("$(jq -n --arg dns fd00:10::1 --arg mtu 1280 --arg puk "$SERVER_PUB" \
        --arg allowed "$ALLOWED" --arg ep "$ENDPOINT" --arg ka 25 \
        --arg prk "$priv" --arg addr "$addr" --arg psk "$psk" \
        '{vpnConfigDefaults: {iDns: $dns, iMtu: $mtu, pPuk: $puk, pAllowedips: $allowed,
                              pEndpoint: $ep, pPersistentKeepAlive: $ka},
          vpnConfigTokens: {iPrk: $prk, iAddr: $addr, pPsk: $psk}}')")
done

printf '%s\n' "${entries[@]}" | jq -s . > "$OUT/vpn.json"
echo "Wrote $OUT/peers.rsc, $OUT/vpn.json and $OUT/index.txt"
```

```bash
# On the HOME computer
cd qr-code-gen
chmod +x gen-peers.sh
./gen-peers.sh
```

Example session:

```
Generate [c]lient or [a]dmin peers? c
How many peers? 3
First address: fd00:10::c:5

About to generate 3 client peer(s): fd00:10::c:5 to fd00:10::c:7
Proceed? [y/N] y
Wrote peers/peers.rsc, peers/vpn.json and peers/index.txt
```

Each run overwrites `peers.rsc`, `vpn.json` and `index.txt` in `peers/`. Finish Steps 30 and 31 for one batch before generating the next.

The `peers/` folder, `vpn*.json` and `qrcode*.png` hold private keys and must never be committed. Make sure `qr-code-gen/.gitignore` lists all three, and confirm with `git status` that nothing new shows up.

### Step 30 — Add the peers on the router

```bash
# On the HOME computer
scp ./peers/peers.rsc admin@192.168.89.1:~/
```

```routeros
/import peers.rsc
/file remove peers.rsc
/interface wireguard peers print proplist=name,allowed-address where name~"^(client|admin)-"
```

**Audit:** every peer must own exactly one `/128`. A wider `allowed-address` (for example a copied `fd00:10::/64`) would let that peer use any source address, including an admin one.

```routeros
:foreach p in=[/interface wireguard peers find] do={
    :foreach a in=[/interface wireguard peers get $p allowed-address] do={
        :local s [:tostr $a]
        :if ([:pick $s ([:find $s "/"] + 1) [:len $s]] != "128") do={
            :put ("Peer " . [/interface wireguard peers get $p name] . " has " . $s)
        }
    }
}
# Expected: no output
```

### Step 31 — Render the QR codes

`main.py` reads `vpn.json` from the current folder and writes one PNG per entry, named after the peer's address: `qrcode-fd00:10::c:5.png`, `qrcode-fd00:10::a:3.png` and so on. The file name already tells which peer it belongs to.

```bash
# On the HOME computer
cd qr-code-gen
python3 -m venv env_python && source env_python/bin/activate
pip install 'qrcode[pil]'
cd peers && python ../main.py
cat index.txt          # PNG file and peer name on the router
```

Each PNG contains a private key. Send each one through a private channel to its owner only, and delete the PNG and `vpn.json` once they are delivered.

### Step 32 — Install on the device

In the app: **Config → scan QR code → grant VPN permission → configure stream URLs** (`http://[fd00:20::cafe]:8889/cam_160/whep` and so on). The tunnel comes up when the Home screen opens.

For a computer (admin with Samba, for example), the same values fit a standard WireGuard config:

```ini
[Interface]
PrivateKey = <iPrk>
Address = fd00:10::a:3/128
DNS = fd00:10::1
MTU = 1280

[Peer]
PublicKey = <pPuk>
PresharedKey = <pPsk>
AllowedIPs = ::/0, 0.0.0.0/0
Endpoint = <vpn-hostname>:14079
PersistentKeepalive = 25
```

### Step 33 — Validate each profile

```routeros
/interface wireguard peers print detail where name=<peer-name>
# last-handshake a few seconds ago, rx/tx growing
```

| Test | Client | Admin |
|---|---|---|
| App shows the cameras | ✅ | ✅ |
| `ping6 fd00:20::cafe` | ✅ | ✅ |
| `dig @fd00:10::1 <site>.cameras.casa AAAA +short` | ✅ `fd00:20::cafe` | ✅ |
| `ssh fd00:10::1` (router) | ❌ timeout | ✅ |
| `ssh`, `smbclient -L`, `curl :8888` on `fd00:20::cafe` | ❌ timeout | ✅ |
| `curl -6 https://ipv6.icanhazip.com` | ❌ | ✅ the router's ether1 address (NAT66) |
| Ping another peer's `fd00:10::` address | ❌ | ❌ |
| On the Pi: `echo $SSH_CLIENT` in a new SSH session | — | the peer's real `fd00:10::` address (no NAT) |
| On the Pi: `sudo journalctl -k --since "-10 min" \| grep 'UFW BLOCK'` after the client tests | — | nothing from the client: the router dropped it first |
| Large Samba copy through the tunnel | — | ✅ no stalls (MSS clamp, Step 19) |

Also run these from inside the home (computer on HOME with the VPN up): the tunnel must work there too, since HOME has IPv6 via NAT66 and the endpoint is AAAA-only.

### Revoking access

```routeros
/interface wireguard peers remove [find name=<peer-name>]
```

The QR code stops working immediately. Reusing the address later is safe, because the old QR code's key no longer exists on the router. Still, prefer the next free index, so that logs and address records stay unambiguous.
