package com.academia.dto;

import com.academia.model.Academia;
import com.academia.model.Perfil;
import com.academia.model.Rede;
import com.academia.model.Usuario;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/** Objetos de entrada e saída do login, do cadastro de academia e da gestão de usuários. */
public final class AuthDtos {

    private AuthDtos() {
    }

    /** dispositivo: token de "lembrar deste dispositivo" que o navegador guardou (opcional). */
    public record LoginRequest(
            @NotBlank(message = "é obrigatório") @Email(message = "inválido") String email,
            @NotBlank(message = "é obrigatória") String senha,
            String dispositivo) {
    }

    /**
     * Cadastro público. tipo = "ACADEMIA" (padrão): cria a academia e o primeiro usuário, que é administrador.
     * tipo = "REDE": cria a rede (nomeRede), a primeira unidade (nomeAcademia) e o dono da rede.
     */
    public record CadastroAcademiaRequest(
            @NotBlank(message = "é obrigatório") @Size(max = 120, message = "máximo de 120 caracteres") String nomeAcademia,
            @Size(max = 80, message = "máximo de 80 caracteres") String cidade,
            @NotBlank(message = "é obrigatório") @Size(max = 120, message = "máximo de 120 caracteres") String nome,
            @NotBlank(message = "é obrigatório") @Email(message = "inválido") @Size(max = 120) String email,
            @NotBlank(message = "é obrigatória") @Size(min = 8, max = 72, message = "deve ter de 8 a 72 caracteres") String senha,
            String tipo,
            @Size(max = 120, message = "máximo de 120 caracteres") String nomeRede) {

        public boolean rede() {
            return "REDE".equalsIgnoreCase(tipo);
        }
    }

    /** Criação/edição de usuário pelo administrador. Na edição, a senha é opcional. */
    public record UsuarioRequest(
            @NotBlank(message = "é obrigatório") @Size(max = 120) String nome,
            @NotBlank(message = "é obrigatório") @Email(message = "inválido") @Size(max = 120) String email,
            @Size(min = 8, max = 72, message = "deve ter de 8 a 72 caracteres") String senha,
            @NotNull(message = "é obrigatório") Perfil perfil,
            Boolean ativo) {
    }

    /** "Meu perfil": a própria pessoa troca o nome (o e-mail, que é o login, só o administrador muda). */
    public record AtualizarPerfilRequest(
            @NotBlank(message = "é obrigatório") @Size(max = 120, message = "máximo de 120 caracteres") String nome) {
    }

    /**
     * Prévia da exclusão da própria conta. escopo: CONTA (só o usuário), ACADEMIA (último admin de uma
     * academia avulsa: leva a academia e todos os dados) ou REDE (dono: leva a rede e todas as unidades).
     * confirmacao é o texto que a pessoa precisa digitar.
     */
    public record ExclusaoContaInfo(String escopo, String confirmacao, String academia, String rede,
                                    int academias, long alunos, long outrosUsuarios, boolean pedeCodigo) {
    }

    public record ExcluirContaRequest(
            @NotBlank(message = "é obrigatória") String senha,
            String codigo,
            @NotBlank(message = "é obrigatória") String confirmacao) {
    }

    /** Sessões abertas da própria conta (navegadores logados agora, contando este). */
    public record SessoesResponse(long abertas) {
    }

    public record TrocarSenhaRequest(
            @NotBlank(message = "é obrigatória") String senhaAtual,
            @NotBlank(message = "é obrigatória") @Size(min = 8, max = 72, message = "deve ter de 8 a 72 caracteres") String novaSenha) {
    }

    public record UsuarioResponse(Long id, String nome, String email, Perfil perfil, boolean ativo, LocalDateTime criadoEm,
                                  boolean doisFatores) {
        public static UsuarioResponse de(Usuario u) {
            return new UsuarioResponse(u.getId(), u.getNome(), u.getEmail(), u.getPerfil(), u.isAtivo(), u.getCriadoEm(),
                    u.doisFatoresAtivo());
        }
    }

    // ---------- Verificação em duas etapas ----------

    /** Segunda etapa do login: o desafio recebido após a senha + o código do aplicativo (ou de recuperação). */
    public record CodigoLoginRequest(
            @NotBlank(message = "é obrigatório") String desafio,
            @NotBlank(message = "é obrigatório") String codigo,
            /** true = lembrar deste dispositivo por 30 dias (não pedir o código nele). */
            Boolean lembrar) {
    }

    public record CodigoRequest(@NotBlank(message = "é obrigatório") String codigo) {
    }

    public record DesativarDoisFatoresRequest(
            @NotBlank(message = "é obrigatória") String senha,
            @NotBlank(message = "é obrigatório") String codigo) {
    }

    public record DoisFatoresStatus(boolean ativo, LocalDateTime ativadoEm, int codigosRestantes,
                                    java.util.List<DispositivoResponse> dispositivos) {
    }

    /** Dispositivo lembrado (o token nunca sai do servidor depois de criado; aqui vai só a descrição). */
    public record DispositivoResponse(Long id, String descricao, LocalDateTime criadoEm, LocalDateTime expiraEm,
                                      LocalDateTime ultimoUsoEm) {
        public static DispositivoResponse de(com.academia.model.DispositivoConfiavel d) {
            return new DispositivoResponse(d.getId(), d.getDescricao(), d.getCriadoEm(), d.getExpiraEm(), d.getUltimoUsoEm());
        }
    }

    /** Dados para configurar o aplicativo: segredo (digitação manual), endereço otpauth e o QR Code em grade. */
    public record DoisFatoresInicio(String segredo, String uri, java.util.List<String> qr) {
    }

    /** Códigos de recuperação em texto: mostrados uma única vez, ao ativar. */
    public record CodigosRecuperacao(java.util.List<String> codigos) {
    }

    public record AcademiaResponse(Long id, String nome, String cidade) {
        public static AcademiaResponse de(Academia a) {
            return new AcademiaResponse(a.getId(), a.getNome(), a.getCidade());
        }
    }

    /** Rede da academia (null se ela for avulsa). */
    public record RedeResponse(Long id, String nome, int limiteUnidades) {
        public static RedeResponse de(Rede r) {
            return r == null ? null : new RedeResponse(r.getId(), r.getNome(), r.getLimiteUnidades());
        }
    }

    /**
     * Resposta do login/cadastro: o token vai no cabeçalho Authorization das próximas requisições.
     * Se o usuário tiver verificação em duas etapas, a senha certa devolve só doisFatores=true e um
     * desafio; o token vem depois, em /api/auth/login/2fa, quando o código do aplicativo conferir.
     */
    /**
     * dispositivo: token novo de "lembrar deste dispositivo" (só quando acabou de ser criado; o navegador guarda).
     * dispositivoExpiraEm: até quando este navegador dispensa o código (criado agora ou reconhecido no login).
     */
    public record SessaoResponse(String token, LocalDateTime expiraEm, UsuarioResponse usuario, AcademiaResponse academia,
                                 boolean doisFatores, String desafio, RedeResponse rede,
                                 String dispositivo, LocalDateTime dispositivoExpiraEm) {
        public static SessaoResponse pedirCodigo(String desafio) {
            return new SessaoResponse(null, null, null, null, true, desafio, null, null, null);
        }

        public SessaoResponse comDispositivo(String token, LocalDateTime ate) {
            return new SessaoResponse(this.token, expiraEm, usuario, academia, doisFatores, desafio, rede, token, ate);
        }
    }

    /** Quem está logado (GET /api/auth/eu). academia = a academia aberta agora (para o dono, a unidade que ele escolheu). */
    public record EuResponse(UsuarioResponse usuario, AcademiaResponse academia, RedeResponse rede) {
    }
}
