import qrcode
import json

def criar_qrcode(texto_entrada, nome_arquivo):
    # Configurações do layout do QR Code
    qr = qrcode.QRCode(
        version=1,  # Controla o tamanho do QR Code (de 1 a 40. 1 é uma matriz 21x21)
        error_correction=qrcode.constants.ERROR_CORRECT_H, # Nível de correção de erro (H permite até 30% de recuperação)
        box_size=10, # Tamanho de cada "quadradinho" em pixels
        border=4,    # Espessura da borda branca (4 é o mínimo padrão)
    )

    # Adiciona a string de entrada ao objeto
    qr.add_data(texto_entrada)
    qr.make(fit=True) # Ajusta o tamanho automaticamente caso o texto seja muito grande

    # Gera a imagem propriamente dita
    imagem = qr.make_image(fill_color="black", back_color="white")

    # Salva a imagem no formato especificado
    imagem.save(nome_arquivo)
    print(f"✅ QR Code gerado e salvo com sucesso como '{nome_arquivo}'!")


with open('vpn.json', 'r', encoding='utf-8') as arquivo:
    vpn_data = json.load(arquivo)


meu_arquivo = "qrcode.png"

criar_qrcode(json.dumps(vpn_data, ensure_ascii=False), meu_arquivo)
