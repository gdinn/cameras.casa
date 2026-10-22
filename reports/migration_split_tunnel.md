# Migrating an existing installation to the split tunnel

For an installation built from an earlier version of [infra_guide.md](infra_guide.md), where every VPN peer used a full tunnel and the admins had internet through home. This guide brings it to the current design: a split tunnel for every peer, and no internet through the VPN for anyone.

- **Do I need it?** Run `/ipv6 firewall nat print where comment~"VPN admin"` on the router. If it prints a rule, yes.
- **Same working rules:** [0.5 Working rules](infra_guide.md#05-working-rules) apply (Safe Mode, select by `comment`, checkpoints, placeholders).
- **Downtime:** none for the app. Each step ends with a test.

---

## What changes

| | Before | Now |
|---|---|---|
| `AllowedIPs` of every peer | `::/0, 0.0.0.0/0` (full tunnel) | `fd00:20::cafe/128, fd00:10::1/128` (the Pi and the router) |
| Internet through the tunnel | Admins: IPv6 only, through NAT66. Clients: none | Nobody |
| Device traffic to the internet while the tunnel is up | Into the tunnel: IPv6 left through home (admins) or was dropped (clients). IPv4 was always dropped, since the tunnel is IPv6 only | Leaves through the device's own network, IPv4 and IPv6 |
| Admin ports on the Pi, router (Step 19) | TCP 22, 80, 443, 445, 8888 | TCP 22, 445, 8888 |
| Admin ports on the Pi, ufw (Step 32) | TCP 22, 80, 445, 8888 | TCP 22, 445, 8888 |
| DNS names (Step 22) | `<site>.cameras.casa` | `<site>.cameras.casa` and `mk.cameras.casa` (the router) |

Nothing else changes: keys, peer addresses, profiles (`fd00:10::a:N` admin, `fd00:10::c:N` client), the endpoint, the MTU, DNS (`fd00:10::1`), SSH to the router and the app ports.

The router stays inside the tunnel because it is the DNS server of every peer (and the SSH target for admins). Without it in `AllowedIPs`, a device loses name resolution while the tunnel is up.

**Order.** Peers first, router second. Once the router change lands, an admin computer still on the full tunnel loses its IPv6 internet while its tunnel is up. Phones can be migrated later, one by one: an old configuration still reaches the Pi, and the phone only loses internet while the app is open, as before.

---

## Step M1 — Admin computers

The WireGuard desktop app keeps the tunnel's configuration, private key included, so the change is made in place.

- **macOS and Windows:** WireGuard app → select the tunnel → **Edit**. Change the `AllowedIPs` line and save.
- **Linux:** edit the `.conf` and restart the tunnel (`sudo wg-quick down ./<peer-name>.conf`, then `up`).

```ini
AllowedIPs = fd00:20::cafe/128, fd00:10::1/128
```

**Test** (on the admin computer, with the tunnel up)

```bash
ssh -i ~/.ssh/cameras_pi <user>@fd00:20::cafe 'echo $SSH_CLIENT'   # fd00:10::a:N ...
curl -6 https://ipv6.icanhazip.com                                  # the local network's address
curl -4 https://ipv4.icanhazip.com                                  # works now too
```

Neither `curl` may print the router's `ether1` address (`/ipv6 address print where interface=ether1` on the router). An error is fine if the local network lacks that IP version.

## Step M2 — Phones (the app)

The app takes `AllowedIPs` only from the QR code, and the QR codes of existing peers were deleted after delivery (Step 28). Issue a **new peer** for each phone:

1. In `qr-code-gen/gen-peers.sh`, set `ALLOWED="fd00:20::cafe/128, fd00:10::1/128"` (Step 26).
2. Run Steps 26–28 with the next free index of the same profile (admin or client).
3. On the phone: **Config → scan QR code** (Step 29). The stream URLs stay as they are.
4. When the app plays on the new peer, revoke the old one ([Revoking access](infra_guide.md#revoking-access)).

**Test** (on the phone, with the app open): the cameras play, and another app (a browser) still loads web pages.

## Step M3 — Router: no internet for the VPN, fewer admin ports

In Safe Mode. Check the two rules to change, then change them:

```routeros
/ipv6 firewall filter print where comment~"VPN admin"
/ipv6 firewall nat print where comment~"VPN admin"
```

```routeros
/ipv6 firewall filter remove [find comment="FORWARD: VPN admin -> Internet (NAT66)"]
/ipv6 firewall filter set [find comment="FORWARD: VPN admin -> Rpi (SSH, web, SMB, HLS)"] \
    dst-port=22,445,8888 comment="FORWARD: VPN admin -> Rpi (SSH, SMB, HLS)"
/ipv6 firewall nat remove [find comment="NAT66: VPN admin -> Internet"]
```

| Change | Reason |
|---|---|
| Forward rule `VPN admin -> Internet` removed | No peer uses home as an internet exit. The final `Drop everything else from VPN` now also covers the internet |
| NAT66 for `vpn-admin` removed | Without the forward rule, it has nothing to translate. Only `NAT66: HOME -> Internet` remains |
| Ports 80 and 443 removed | Nothing on the Pi serves them. MediaMTX's HLS (8888) is the admins' web access |

**Test**

```routeros
/ipv6 firewall filter print where comment~"VPN admin"
# Only "FORWARD: VPN admin -> Rpi (SSH, SMB, HLS)", with dst-port=22,445,8888
/ipv6 firewall nat print
# Only "NAT66: HOME -> Internet (no prefix delegation from ISP)"
```

Save a checkpoint (`checkpoint-split-tunnel`).

## Step M4 — Pi: close port 80 in ufw

From an admin SSH session through the VPN:

```bash
# On the Pi
sudo ufw delete allow from fd00:10::a:0/112 to any port 80 proto tcp
```

If `ufw` answers `Could not delete non-existent rule`, find the rule with `sudo ufw status numbered` and delete it by number (`sudo ufw delete <n>`).

**Test**

```bash
# On the Pi
sudo ufw show added
# Exactly 5 rules: 8889/tcp and 8189/udp from fd00:10::/64; 22, 445, 8888/tcp from fd00:10::a:0/112
```

## Step M5 — DNS name for the router

```routeros
/ip dns static add address=fd00:10::1 name=mk.cameras.casa ttl=3m type=AAAA
```

**Test** (on an admin computer, with the tunnel up)

```bash
dig AAAA mk.cameras.casa @fd00:10::1 +short               # fd00:10::1
ssh -i ~/.ssh/cameras_router admin@mk.cameras.casa
```

## Step M6 — Validate both profiles

Repeat the table of [Step 30](infra_guide.md#step-30--validate-each-profile) with one admin and one client on the new configuration. Two rows changed against the old guide:

- `curl -6` / `curl -4` to icanhazip: the **local** network's addresses for both profiles, never the router's `ether1` address.
- Admin ports: only 22, 445 and 8888.

---

## The Pi's services

Earlier versions of the guide had no Parts 10–13: MediaMTX was assumed to be running already. To bring the Pi to the documented setup (ext4 recordings disk, MediaMTX, recorder, Samba), follow [pi_services_guide.md](pi_services_guide.md) from Part 10. The documented MediaMTX paths are `cam_10` to `cam_13`, named after the camera addresses. If the Pi used other names, update the stream URLs in the app (Step 29).
