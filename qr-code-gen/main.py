import argparse
import json
from pathlib import Path

import qrcode


def create_qrcode(input_text, file_name):
    # QR Code layout settings
    qr = qrcode.QRCode(
        version=1,  # Controls the QR Code size (1 to 40; 1 is a 21x21 matrix)
        error_correction=qrcode.constants.ERROR_CORRECT_H,  # Error correction level (H allows up to 30% recovery)
        box_size=10,  # Size of each "box" in pixels
        border=4,     # Thickness of the white border (4 is the standard minimum)
    )

    # Add the input string to the object
    qr.add_data(input_text)
    qr.make(fit=True)  # Automatically adjusts the size if the text is too large

    # Generate the actual image
    image = qr.make_image(fill_color="black", back_color="white")

    # Save the image in the specified format
    image.save(file_name)
    print(f"✅ QR Code generated and successfully saved as '{file_name}'!")


def generate_from_vpn_json():
    """One QR code per peer of 'vpn.json' (real keys: the file is never committed)."""
    with open('vpn.json', 'r', encoding='utf-8') as file:
        vpn_data = json.load(file)

    if not isinstance(vpn_data, list):
        raise ValueError("'vpn.json' must contain a JSON array.")

    # Each QR code is named after the peer's tunnel address (e.g. qrcode-fd00:10::c:5.png).
    # Every entry is checked before any file is written, so a bad entry never leaves a partial batch.
    addresses = []
    for number, item in enumerate(vpn_data):
        i_addr = item.get("vpnConfigTokens", {}).get("iAddr") if isinstance(item, dict) else None
        address = i_addr.split("/")[0].strip() if isinstance(i_addr, str) else ""
        if not address:
            raise ValueError(f"Entry {number} in 'vpn.json' has no vpnConfigTokens.iAddr.")
        addresses.append(address)

    for item, address in zip(vpn_data, addresses):
        create_qrcode(json.dumps(item, ensure_ascii=False), f"qrcode-{address}.png")

    print(f"🎉 Done! {len(vpn_data)} QR Code(s) generated.")


EXAMPLES_DIR = Path(__file__).parent / "examples"


def generate_examples():
    """One QR code per fictional payload of examples/, next to its JSON.

    The file's text goes into the QR code exactly as written, not re-serialized: the instrumented
    QrCodeImageDecodingTest compares what ML Kit reads from the PNG with the JSON file byte for byte.
    It never reads 'vpn.json'.
    """
    json_files = sorted(EXAMPLES_DIR.glob("*.json"))
    if not json_files:
        raise ValueError(f"No JSON file in '{EXAMPLES_DIR}'.")
    for json_file in json_files:
        create_qrcode(json_file.read_text(encoding="utf-8"), str(json_file.with_suffix(".png")))
    print(f"🎉 Done! {len(json_files)} example QR Code(s) generated.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Generate the app's WireGuard QR codes.")
    parser.add_argument(
        "--examples",
        action="store_true",
        help="regenerate the fictional test QR codes of examples/ instead of reading vpn.json",
    )
    if parser.parse_args().examples:
        generate_examples()
    else:
        generate_from_vpn_json()
