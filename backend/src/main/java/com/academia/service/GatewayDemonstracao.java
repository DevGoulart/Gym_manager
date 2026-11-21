package com.academia.service;

import com.academia.dto.AssinaturaDtos.CartaoRequest;
import com.academia.exception.RegraNegocioException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Gateway de pagamento de DEMONSTRAÇÃO: nenhum valor é cobrado e nada sai do servidor.
 * Valida o cartão como um gateway faria (número pelo algoritmo de Luhn, validade e código),
 * gera um Pix copia e cola e uma linha digitável de boleto com a cara dos reais.
 *
 * Cartões de teste: qualquer número válido é aprovado; os terminados em 0002
 * (ex.: 4000 0000 0000 0002) são recusados, para testar o caminho de recusa.
 * Para usar um gateway de verdade, troque esta classe por uma integração (Mercado Pago, Stripe...).
 */
@Component
public class GatewayDemonstracao {

    private static final SecureRandom ALEATORIO = new SecureRandom();

    /** Bandeira e últimos 4 dígitos: tudo o que fica gravado do cartão. */
    public record CartaoAprovado(String bandeira, String finalNumero) {
    }

    public CartaoAprovado cobrarCartao(CartaoRequest c, BigDecimal valor) {
        if (c == null) throw new RegraNegocioException("Informe os dados do cartão.");
        String nome = c.nome() == null ? "" : c.nome().trim();
        if (nome.split("\\s+").length < 2) throw new RegraNegocioException("Nome no cartão: informe como está impresso (nome e sobrenome).");
        String numero = c.numero() == null ? "" : c.numero().replaceAll("[\\s.-]", "");
        if (!numero.matches("\\d{13,19}") || !luhn(numero)) throw new RegraNegocioException("Número do cartão inválido.");
        String bandeira = bandeira(numero);
        validarValidade(c.validade());
        String cvv = c.cvv() == null ? "" : c.cvv().trim();
        int digitosCvv = "Amex".equals(bandeira) ? 4 : 3;
        if (!cvv.matches("\\d{" + digitosCvv + "}")) throw new RegraNegocioException("Código de segurança (CVV) inválido: são " + digitosCvv + " dígitos.");
        if (numero.endsWith("0002")) {
            throw new RegraNegocioException("Número do cartão: pagamento recusado pelo emissor. (Na demonstração, cartões terminados em 0002 são recusados.)");
        }
        return new CartaoAprovado(bandeira, numero.substring(numero.length() - 4));
    }

    private static void validarValidade(String validade) {
        String v = validade == null ? "" : validade.trim();
        if (!v.matches("(0[1-9]|1[0-2])\\s*/\\s*(\\d{2}|\\d{4})")) throw new RegraNegocioException("Validade do cartão inválida: use MM/AA.");
        String[] p = v.split("/");
        int mes = Integer.parseInt(p[0].trim()), ano = Integer.parseInt(p[1].trim());
        if (ano < 100) ano += 2000;
        if (YearMonth.of(ano, mes).isBefore(YearMonth.now())) throw new RegraNegocioException("Validade do cartão: este cartão está vencido.");
    }

    static boolean luhn(String numero) {
        int soma = 0;
        boolean dobrar = false;
        for (int i = numero.length() - 1; i >= 0; i--) {
            int d = numero.charAt(i) - '0';
            if (dobrar) { d *= 2; if (d > 9) d -= 9; }
            soma += d;
            dobrar = !dobrar;
        }
        return soma % 10 == 0;
    }

    static String bandeira(String n) {
        if (n.matches("^(4011|4312|4389|4514|4576|5041|5066|5067|509\\d|6277|6362|6363|650\\d|6516|6550).*")) return "Elo";
        if (n.matches("^(606282|3841).*")) return "Hipercard";
        if (n.matches("^3[47].*")) return "Amex";
        if (n.matches("^4.*")) return "Visa";
        if (n.matches("^(5[1-5]|222[1-9]|22[3-9]\\d|2[3-6]\\d\\d|27[01]\\d|2720).*")) return "Mastercard";
        return "Cartão";
    }

    /** Pix copia e cola no formato EMV do Banco Central (chave aleatória fictícia), com CRC16 válido. */
    public String gerarPix(BigDecimal valor, long pedido) {
        String chave = UUID.randomUUID().toString();
        String conta = campo("00", "br.gov.bcb.pix") + campo("01", chave);
        String txid = campo("05", "FT" + pedido);
        String semCrc = campo("00", "01") + campo("26", conta) + campo("52", "0000") + campo("53", "986")
                + campo("54", valor.setScale(2, RoundingMode.HALF_UP).toPlainString()) + campo("58", "BR")
                + campo("59", "FORCA TOTAL DEMO") + campo("60", "SAO PAULO") + campo("62", txid) + "6304";
        return semCrc + String.format("%04X", crc16(semCrc));
    }

    private static String campo(String id, String valor) {
        return id + String.format("%02d", valor.length()) + valor;
    }

    /** CRC16-CCITT (polinômio 0x1021, início 0xFFFF), o usado pelo Pix. */
    static int crc16(String s) {
        int crc = 0xFFFF;
        for (byte b : s.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            crc ^= (b & 0xFF) << 8;
            for (int i = 0; i < 8; i++) crc = (crc & 0x8000) != 0 ? ((crc << 1) ^ 0x1021) & 0xFFFF : (crc << 1) & 0xFFFF;
        }
        return crc;
    }

    /** Linha digitável fictícia (47 dígitos) com o fator de vencimento e o valor no fim, como num boleto real. */
    public String gerarBoleto(BigDecimal valor, LocalDate vencimento) {
        StringBuilder b = new StringBuilder("00190"); // banco e moeda
        while (b.length() < 32) b.append(ALEATORIO.nextInt(10));
        long fator = ChronoUnit.DAYS.between(LocalDate.of(2022, 5, 29), vencimento) + 1000; // fator de vencimento (base nova)
        String valorCentavos = String.format("%010d", valor.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValue());
        b.append(1 + ALEATORIO.nextInt(9)).append(String.format("%04d", fator % 10000)).append(valorCentavos);
        String d = b.toString(); // 47 dígitos
        return d.substring(0, 5) + "." + d.substring(5, 10) + " " + d.substring(10, 15) + "." + d.substring(15, 21) + " "
                + d.substring(21, 26) + "." + d.substring(26, 32) + " " + d.charAt(32) + " " + d.substring(33);
    }
}
