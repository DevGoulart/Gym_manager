package com.academia.service;

import com.academia.model.*;
import com.academia.model.Notificacao.Nivel;
import com.academia.model.Notificacao.Tipo;
import com.academia.repository.AcademiaRepository;
import com.academia.repository.NotificacaoLidaRepository;
import com.academia.repository.NotificacaoRepository;
import com.academia.repository.UsuarioRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Grava notificações de eventos (o que aconteceu): pagamento aprovado, senha alterada,
 * verificação em duas etapas ligada, desligada ou redefinida. Não depende de outros services,
 * para qualquer um poder avisar sem criar dependência circular.
 */
@Service
public class Notificador {

    private static final NumberFormat MOEDA = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"));

    private final NotificacaoRepository notificacoes;
    private final NotificacaoLidaRepository lidas;
    private final UsuarioRepository usuarios;
    private final AcademiaRepository academias;

    public Notificador(NotificacaoRepository notificacoes, NotificacaoLidaRepository lidas,
                       UsuarioRepository usuarios, AcademiaRepository academias) {
        this.notificacoes = notificacoes;
        this.lidas = lidas;
        this.usuarios = usuarios;
        this.academias = academias;
    }

    static String moeda(BigDecimal v) {
        synchronized (MOEDA) {
            return MOEDA.format(v).replace(' ', ' ');
        }
    }

    public void avisar(Long usuarioId, Tipo tipo, Nivel nivel, String titulo, String texto, String link) {
        Notificacao n = new Notificacao();
        n.setUsuarioId(usuarioId);
        n.setTipo(tipo);
        n.setNivel(nivel);
        n.setTitulo(titulo);
        n.setTexto(texto);
        n.setLink(link);
        notificacoes.save(n);
    }

    /** Quem cuida da assinatura: o dono da rede, ou os administradores da academia avulsa. */
    private List<Usuario> gestores(Assinatura s) {
        List<Usuario> lista = new ArrayList<>();
        if (s.getRede() != null) {
            for (Academia a : academias.findByRedeIdOrderByIdAsc(s.getRede().getId())) {
                lista.addAll(usuarios.findByAcademiaIdAndPerfilOrderByNomeAsc(a.getId(), Perfil.DONO));
            }
        } else if (s.getAcademia() != null) {
            lista.addAll(usuarios.findByAcademiaIdAndPerfilOrderByNomeAsc(s.getAcademia().getId(), Perfil.ADMIN));
        }
        return lista;
    }

    public void pagamentoAprovado(Assinatura s, Pagamento p) {
        String texto = p.getDescricao() + " · " + moeda(p.getValor())
                + (p.getPeriodoFim() != null ? " · válido até " + p.getPeriodoFim().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "");
        for (Usuario u : gestores(s)) {
            avisar(u.getId(), Tipo.ASSINATURA, Nivel.INFO, "Pagamento aprovado", texto, "#/assinatura");
        }
    }

    public void senhaAlterada(Long usuarioId, String porQuem) {
        avisar(usuarioId, Tipo.SEGURANCA, Nivel.ATENCAO,
                porQuem == null ? "Sua senha foi alterada" : porQuem + " alterou a sua senha",
                porQuem == null ? "Se não foi você, fale com um administrador." : "Use a senha nova para entrar. Se estranhar, fale com quem administra o sistema.",
                "#/perfil");
    }

    public void doisFatoresAtivada(Long usuarioId) {
        avisar(usuarioId, Tipo.SEGURANCA, Nivel.INFO, "Verificação em duas etapas ativada",
                "O login passa a pedir também o código do aplicativo. Guarde os códigos de recuperação.", "#/perfil");
    }

    public void doisFatoresDesativada(Long usuarioId, String porQuem) {
        avisar(usuarioId, Tipo.SEGURANCA, Nivel.ATENCAO,
                porQuem == null ? "Verificação em duas etapas desligada" : porQuem + " redefiniu a sua verificação em duas etapas",
                porQuem == null ? "Sua conta agora está protegida só pela senha." : "Você entra só com a senha e pode ativar de novo em Meu perfil.",
                "#/perfil");
    }

    public void sessoesEncerradas(Long usuarioId) {
        avisar(usuarioId, Tipo.SEGURANCA, Nivel.INFO, "Outras sessões encerradas",
                "Sua conta continua aberta só no navegador em que você fez isso.", "#/perfil");
    }

    /** A conta foi apagada: some com as notificações dela. */
    public void apagarDoUsuario(Long usuarioId) {
        notificacoes.apagarDoUsuario(usuarioId);
        lidas.apagarDoUsuario(usuarioId);
    }
}
