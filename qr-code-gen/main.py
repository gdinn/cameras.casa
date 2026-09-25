import qrcode
import json


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


with open('vpn.json', 'r', encoding='utf-8') as file:
    vpn_data = json.load(file)

if not isinstance(vpn_data, list):
    raise ValueError("'vpn.json' must contain a JSON array.")

for number, item in enumerate(vpn_data):
    file_name = f"qrcode-{number}.png"
    create_qrcode(json.dumps(item, ensure_ascii=False), file_name)

print(f"🎉 Done! {len(vpn_data)} QR Code(s) generated.")