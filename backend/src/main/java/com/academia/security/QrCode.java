package com.academia.security;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Gera o QR Code como uma grade de módulos ("1" = quadrado preto). O navegador desenha a grade em SVG,
 * sem precisar de imagem nem de biblioteca no front-end.
 */
public final class QrCode {

    private QrCode() {
    }

    public static List<String> linhas(String conteudo) {
        try {
            BitMatrix m = new QRCodeWriter().encode(conteudo, BarcodeFormat.QR_CODE, 0, 0,
                    Map.of(EncodeHintType.MARGIN, 2, EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M));
            List<String> linhas = new ArrayList<>();
            for (int y = 0; y < m.getHeight(); y++) {
                StringBuilder sb = new StringBuilder(m.getWidth());
                for (int x = 0; x < m.getWidth(); x++) sb.append(m.get(x, y) ? '1' : '0');
                linhas.add(sb.toString());
            }
            return linhas;
        } catch (WriterException e) {
            throw new IllegalStateException("Não foi possível gerar o QR Code", e);
        }
    }
}
