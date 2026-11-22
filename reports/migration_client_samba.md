# Migrating an existing installation to Samba for client peers

For an installation built from an earlier version of [infra_guide.md](infra_guide.md) and [pi_services_guide.md](pi_services_guide.md), where only admin peers (`fd00:10::a:N`) reached Samba. This guide brings it to the current design: client peers (`fd00:10::c:N`) also reach Samba, read only, and the generator can give a client batch Samba logins.

- **Do I need it?** Run `/ipv6 firewall filter print where comment~"VPN client"` on the router. If it prints nothing, yes.
- **Before:** the Pi follows Parts 10–13 of [pi_services_guide.md](pi_services_guide.md). A Pi that never went through Part 13 needs no Step C1: it gets the current `smb.conf` when Part 13 is followed.
- **Split tunnel:** not required. This guide and [migration_split_tunnel.md](migration_split_tunnel.md) touch different rules and can be followed in either order. Where a test depends on it (Steps C2 and C6), the expected result for an installation still on the full tunnel is given too.
- **Same working rules:** [0.5 Working rules](infra_guide.md#05-working-rules) apply (Safe Mode, select by `comment`, checkpoints, placeholders).
- **Downtime:** none, for the app or for admins. Each step ends with a test.

---

## What changes

| | Before | Now |
|---|---|---|
| Client peers (`fd00:10::c:0/112`) on the Pi | TCP 8889, UDP 8189, ping | + TCP 445 (Samba), read only |
| Router address list `vpn-clients` | Documentation only, no rule uses it | Used by `FORWARD: VPN client -> Rpi (SMB)` (Step 19) |
| ufw on the Pi (Step 32) | 5 rules | 6 rules: + 445/TCP from `fd00:10::c:0/112` |
| Samba `hosts allow` (Step 43) | `fd00:10::a:0/112 ::1 127.0.0.1` | `fd00:10::a:0/112 fd00:10::c:0/112 ::1 127.0.0.1` |
| `gen-peers.sh` (Step 26) | Samba login for admin batches only | Also for client batches, when you answer yes |
| App | History and downloads for "admins" | History and downloads for any QR code with a Samba login |

Nothing else changes: admins keep SSH, Samba and HLS; clients still get no SSH and no HLS; an address outside both blocks gets no Samba; the share, its group (`smb_viewer`) and its permissions stay the same, so clients see every camera, read only, exactly like admins.

**Existing client peers** keep working as before, without the recordings: the app reads the Samba login only from the QR code, and their QR codes carry none. A client that should see the recordings gets a **new peer** from a batch with logins (Step C5), and the old peer is revoked.

**The app** needs no new version for this: it already shows the recordings to any QR code with a Samba login. The newer version only stops calling those users "admins" in its messages.

**Order.** Pi first, router last. Each layer alone opens nothing for clients, so the order only decides when access starts: with the router last, it starts once Samba and ufw are both ready.

---

## Step C1 — Pi: Samba accepts the client block

From an admin SSH session through the VPN:

```bash
# On the Pi
sudo cp /etc/samba/smb.conf /etc/samba/smb.conf.bak-clients
sudo sed -i 's|^\(\s*hosts allow = fd00:10::a:0/112\) |\1 fd00:10::c:0/112 |' /etc/samba/smb.conf
testparm -s 2>/dev/null | grep 'hosts allow'
sudo systemctl reload smbd
```

`sed` adds the client block right after the admin block, on the `hosts allow` line only. If `grep` does not print the client block, the line differed from the documented one: edit it by hand (`sudoedit /etc/samba/smb.conf`) to match [`nvr-setup/smb.conf`](../nvr-setup/smb.conf).

| Change | Reason |
|---|---|
| `fd00:10::c:0/112` in `hosts allow` | Samba's own check of the source, the third layer after the router and ufw. Same block as the router's `vpn-clients` list |
| `reload`, not `restart` | `smbd` reads the new rule for every new connection; admins already connected keep their session |

**Test**

```bash
# On the Pi
testparm -s 2>/dev/null | grep 'hosts allow'
# hosts allow = fd00:10::a:0/112 fd00:10::c:0/112 ::1 127.0.0.1
systemctl is-active smbd                     # active
smbclient //localhost/backup_cams -U <admin-user> -c 'ls'   # cam_10 ... cam_13 (<admin-user>: an admin's Samba user, e.g. admin-a-1)
```

## Step C2 — Pi: ufw lets the client block reach 445

```bash
# On the Pi
sudo ufw allow from fd00:10::c:0/112 to any port 445 proto tcp comment 'VPN clients: Samba'
```

Only 445: the client block gets no rule for 22 or 8888.

**Test**

```bash
# On the Pi
sudo ufw show added
# Exactly 6 rules: 8889/tcp and 8189/udp from fd00:10::/64; 22, 445, 8888/tcp from fd00:10::a:0/112;
# 445/tcp from fd00:10::c:0/112
```

Still on the full tunnel (Step M4 of the split tunnel migration not done yet): 7 rules, the 6 above plus 80/tcp from `fd00:10::a:0/112`. M4 removes it later.

## Step C3 — Router: forward Samba from the client block

In Safe Mode. Check the address list and the rule the new one goes before:

```routeros
/ipv6 firewall address-list print where list=vpn-clients
# fd00:10::c:0/112
/ipv6 firewall filter print where comment="FORWARD: Drop everything else from VPN"
```

If the address list is missing, add it as in [Step 17](infra_guide.md#step-17--address-lists-and-anti-spoofing-raw). Then:

```routeros
/ipv6 firewall address-list set [find list=vpn-clients] comment="VPN clients: fd00:10::c:N"
/ipv6 firewall filter add action=accept chain=forward dst-address-list=cameras.casa dst-port=445 \
    in-interface=wireguard-ipv6 protocol=tcp src-address-list=vpn-clients \
    comment="FORWARD: VPN client -> Rpi (SMB)" \
    place-before=[find comment="FORWARD: Drop everything else from VPN"]
```

| Change | Reason |
|---|---|
| New forward rule, port 445 only | Clients gain Samba and nothing else. The admin rule (`22,445,8888` from `vpn-admin`) stays as it is |
| `place-before` the VPN drop | A rule after `Drop everything else from VPN` would never match |
| `src-address-list=vpn-clients`, not all of `fd00:10::/64` | An address outside both blocks keeps getting only the app ports |
| Comment of `vpn-clients` | It no longer says "Documentation only": a rule uses it now |

**Test**

```routeros
/ipv6 firewall filter print where comment~"FORWARD: (VPN|Drop everything else from VPN)"
# In this order: ... "FORWARD: VPN admin -> Rpi (SSH, SMB, HLS)",
# "FORWARD: VPN client -> Rpi (SMB)", "FORWARD: Drop everything else from VPN"
```

Save a checkpoint (`checkpoint-client-samba`).

## Step C4 — Update the generator

The generator lives in [Step 26 of infra_guide.md](infra_guide.md#step-26--generate-keys-and-entries-interactive-script). Replace the local `qr-code-gen/gen-peers.sh` on the HOME computer with the current version from that step, and fill in again the two values at the top (`SERVER_PUB` and `ENDPOINT`, the same as in the old copy).

What changed: for a client batch it now asks `Give these clients a Samba login (read-only recordings in the app)? [y/N]`. With `y`, each client gets a Samba user named after its peer (`client-c-N`), the login goes into its QR code (`smbCredentials`), and the script writes `peers/pi-smb-users.sh`, exactly as for admins. With `N` (the default), the batch is generated as before. Admin batches do not ask: they always get a login.

**Test** (on the HOME computer, without importing anything)

```bash
# On the HOME computer, from qr-code-gen/
grep -c 'Give these clients a Samba login' gen-peers.sh     # 1
```

## Step C5 — Client peers with access to the recordings

For each client that should see the recordings, issue a new peer:

1. Run `gen-peers.sh` (Step 26) with profile `c`, answer `y` to the Samba question, and use the next free client index.
2. Add the peers on the router and render the QR codes (Steps 27 and 28).
3. Create the batch's Samba users on the Pi with `pi-smb-users.sh` ([Step 44 of pi_services_guide.md](pi_services_guide.md#step-44--samba-users)), and delete the script on both sides.
4. On the phone: **Config → scan QR code** (Step 29). The stream URLs stay as they are.
5. When the app shows the recordings on the new peer, revoke the old one ([Revoking access](infra_guide.md#revoking-access)).

**Test** (on the phone, with the app open): the cameras play; a camera's history button lists the recordings, and one plays and downloads.

## Step C6 — Validate both profiles

Repeat the table of [Step 30](infra_guide.md#step-30--validate-each-profile) with one admin and one client on the new configuration, then the share tests of [Step 45](pi_services_guide.md#step-45--test-from-the-pi-and-from-the-admin-computer) with the client's Samba user. Rows that changed against the old guide:

- `curl` on `:445`: ✅ `200` for the client too (with the stand-in server; with Samba running, use the Step 45 tests instead).
- `curl` on `:8888` and SSH to the Pi and the router: still ❌ for the client.
- Samba login with a **client's** user, from a client tunnel: ✅ the four camera folders, read only.

Still on the full tunnel (split tunnel migration not done yet), two rows of the Step 30 table differ, and neither is about Samba: an admin on an old full-tunnel configuration gets the router's `ether1` address from `curl -6` to icanhazip (IPv6 internet through home), and the admin's ports also include 80 and 443. They become as in the table after [migration_split_tunnel.md](migration_split_tunnel.md). The client peers from Step C5 already use the split tunnel, so their rows match the table either way.

On the Pi, check that Samba sees the client's real address:

```bash
# On the Pi, while the client's app or computer has the share open
sudo smbstatus -b
# <client-c-N>  smb_viewer  fd00:10::c:N ...
```
