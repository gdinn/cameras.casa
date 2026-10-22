#!/usr/bin/env python3
"""Copy the guides from reports/ into the Pages site before the Jekyll build.

reports/*.md stays the only source of the guides: this copy is made in CI, into pages/guides/
(ignored by git), and never committed. Each copy gets the front matter the site needs, and the
links between the guides (`pi_services_guide.md#part-10...`) become relative links to the pages'
permalinks (`../pi-services-guide/#part-10...`), and a link whose text is the file name reads as
the guide's name instead. jekyll-relative-links would also convert them,
but into absolute URLs with the base path, and every link in this site is relative.

Usage, from the repository root: python3 .github/scripts/copy-guides-to-pages.py
"""
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "reports"
TARGET = ROOT / "pages" / "guides"

# Source file -> (permalink, page title, description, link text)
GUIDES = {
    "infra_guide.md": (
        "/infra-guide/",
        "Infrastructure guide — Cameras App",
        "Step-by-step guide to the MikroTik router, firewalled zones, IPv6-only WireGuard VPN "
        "and Raspberry Pi firewall behind Cameras App.",
        "the infrastructure guide",
    ),
    "pi_services_guide.md": (
        "/pi-services-guide/",
        "Raspberry Pi services guide — Cameras App",
        "Step-by-step guide to the recordings disk, MediaMTX, the recorder and Samba on the "
        "Raspberry Pi behind Cameras App.",
        "the Raspberry Pi services guide",
    ),
    "migration_split_tunnel.md": (
        "/migration-split-tunnel/",
        "Migrating to the split tunnel — Cameras App",
        "Bringing an installation built from an earlier version of the infrastructure guide "
        "to the split tunnel.",
        "the migration guide",
    ),
}

CODE_SPAN = re.compile(r"`[^`]*`")
GUIDE_NAMES = "|".join(re.escape(name) for name in GUIDES)
GUIDE_LINK = re.compile(r"\[([^\]]*)\]\((" + GUIDE_NAMES + r")(#[^)]*)?\)")


def unescape_table_pipes(line: str) -> str:
    # GitHub needs "\|" for a pipe inside a code span in a table; kramdown prints it literally.
    if not line.startswith("|"):
        return line
    return CODE_SPAN.sub(lambda m: m.group(0).replace("\\|", "|"), line)


def relative_guide_links(text: str) -> str:
    # Every guide page sits one level below the site root, so "../" reaches its siblings.
    def repl(match: re.Match) -> str:
        text, name, fragment = match.group(1), match.group(2), match.group(3) or ""
        permalink, _title, _description, link_text = GUIDES[name]
        if text == name:
            text = link_text
        return f"[{text}](../{permalink.strip('/')}/{fragment})"

    return GUIDE_LINK.sub(repl, text)


def front_matter(permalink: str, title: str, description: str) -> str:
    return (
        "---\n"
        "layout: default\n"
        f'title: "{title}"\n'
        f'description: "{description}"\n'
        "lang: en\n"
        f"permalink: {permalink}\n"
        'home_href: "../"\n'
        "body_class: guide\n"
        "---\n\n"
    )


def main() -> None:
    TARGET.mkdir(parents=True, exist_ok=True)
    for name, (permalink, title, description, _link_text) in GUIDES.items():
        text = (SOURCE / name).read_text(encoding="utf-8")
        text = "\n".join(unescape_table_pipes(line) for line in text.split("\n"))
        text = relative_guide_links(text)
        # The guides are documentation, not templates: nothing in them is Liquid.
        page = front_matter(permalink, title, description) + "{% raw %}\n" + text + "\n{% endraw %}\n"
        (TARGET / name).write_text(page, encoding="utf-8")
        print(f"{name} -> pages/guides/{name} ({permalink})")


if __name__ == "__main__":
    main()
