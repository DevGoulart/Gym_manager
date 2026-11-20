package com.academia.dto;

import com.academia.dto.AuthDtos.RedeResponse;
import com.academia.dto.AuthDtos.UsuarioResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/** Entrada e saída do painel da rede (plano Rede): unidades, administradores e troca de unidade. */
public final class RedeDtos {

    private RedeDtos() {
    }

    /** Administrador de uma unidade, cadastrado pelo dono da rede. */
    public record AdminRequest(
            @NotBlank(message = "é obrigatório") @Size(max = 120, message = "máximo de 120 caracteres") String nome,
            @NotBlank(message = "é obrigatório") @Email(message = "inválido") @Size(max = 120) String email,
            @NotBlank(message = "é obrigatória") @Size(min = 8, max = 72, message = "deve ter de 8 a 72 caracteres") String senha) {
    }

    /** Nova unidade: toda unidade nasce com o seu administrador. */
    public record UnidadeRequest(
            @NotBlank(message = "é obrigatório") @Size(max = 120, message = "máximo de 120 caracteres") String nome,
            @Size(max = 80, message = "máximo de 80 caracteres") String cidade,
            @NotNull(message = "é obrigatório") @Valid AdminRequest admin) {
    }

    /** Edição do nome e da cidade de uma unidade. */
    public record UnidadeEdicaoRequest(
            @NotBlank(message = "é obrigatório") @Size(max = 120, message = "máximo de 120 caracteres") String nome,
            @Size(max = 80, message = "máximo de 80 caracteres") String cidade) {
    }

    /** O administrador de uma academia avulsa transforma a academia em rede (e vira o dono). */
    public record ConverterRedeRequest(
            @NotBlank(message = "é obrigatório") @Size(max = 120, message = "máximo de 120 caracteres") String nomeRede) {
    }

    /** Uma unidade no painel do dono: os números do dia e quem administra. */
    public record UnidadeResumo(Long id, String nome, String cidade, boolean aberta, boolean sede,
                                DashboardResponse numeros, List<UsuarioResponse> administradores, long usuarios) {
    }

    /** Soma de todas as unidades. */
    public record TotaisRede(long unidades, long alunosAtivos, BigDecimal recebidoNoMes, long mensalidadesAtrasadas,
                             BigDecimal valorEmAberto, long professores) {
    }

    public record PainelRede(RedeResponse rede, List<UnidadeResumo> unidades, TotaisRede totais) {
    }
}
