package com.academia.service;

import com.academia.dto.AuthDtos.ExclusaoContaInfo;
import com.academia.dto.AuthDtos.ExcluirContaRequest;
import com.academia.exception.NaoAutenticadoException;
import com.academia.exception.RegraNegocioException;
import com.academia.model.Academia;
import com.academia.model.Perfil;
import com.academia.model.Rede;
import com.academia.model.Usuario;
import com.academia.repository.AcademiaRepository;
import com.academia.repository.AlunoRepository;
import com.academia.repository.RedeRepository;
import com.academia.repository.UsuarioRepository;
import com.academia.security.AcademiaAtual;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * Exclusão da própria conta, pela página "Meu perfil".
 *
 * O alcance depende de quem pede:
 *  - CONTA:    funcionário, ou administrador que não é o último da academia, ou administrador de
 *              uma unidade de rede (o dono continua cuidando dela). Apaga só o usuário.
 *  - ACADEMIA: último administrador ativo de uma academia avulsa. Apaga a academia inteira:
 *              alunos, mensalidades, treinos, avaliações, professores, planos e os outros usuários.
 *  - REDE:     dono da rede. Apaga a rede com todas as unidades e tudo o que há nelas.
 *
 * Para confirmar, pede a senha, o código das duas etapas (se estiver ativa) e um texto digitado:
 * "EXCLUIR" para a conta, o nome da academia ou o nome da rede nos outros casos.
 */
@Service
public class ExclusaoContaService {

    public static final String CONFIRMACAO_CONTA = "EXCLUIR";

    private final UsuarioRepository usuarios;
    private final AcademiaRepository academias;
    private final RedeRepository redes;
    private final AlunoRepository alunos;
    private final RemocaoDeAcademias remocao;
    private final DoisFatoresService doisFatores;
    private final AcademiaAtual academiaAtual;

    public ExclusaoContaService(UsuarioRepository usuarios, AcademiaRepository academias, RedeRepository redes,
                                AlunoRepository alunos, RemocaoDeAcademias remocao, DoisFatoresService doisFatores,
                                AcademiaAtual academiaAtual) {
        this.usuarios = usuarios;
        this.academias = academias;
        this.redes = redes;
        this.alunos = alunos;
        this.remocao = remocao;
        this.doisFatores = doisFatores;
        this.academiaAtual = academiaAtual;
    }

    private enum Escopo { CONTA, ACADEMIA, REDE }

    /** O que será apagado: calculado igual na prévia e na exclusão. */
    private record Alcance(Escopo escopo, List<Academia> academias, Rede rede, String confirmacao) {
    }

    private Usuario eu() {
        return usuarios.findById(academiaAtual.usuario().id())
                .orElseThrow(() -> new NaoAutenticadoException("Faça login para continuar."));
    }

    private Alcance alcance(Usuario u) {
        Academia casa = u.getAcademia();
        if (u.getPerfil() == Perfil.DONO && casa.getRede() != null) {
            Rede rede = casa.getRede();
            return new Alcance(Escopo.REDE, academias.findByRedeIdOrderByIdAsc(rede.getId()), rede, rede.getNome());
        }
        if (u.getPerfil() == Perfil.ADMIN && casa.getRede() == null) {
            long outrosAdmins = usuarios.countByAcademiaIdAndPerfilAndAtivoTrue(casa.getId(), Perfil.ADMIN)
                    + usuarios.countByAcademiaIdAndPerfilAndAtivoTrue(casa.getId(), Perfil.DONO)
                    - (u.isAtivo() ? 1 : 0);
            if (outrosAdmins <= 0) return new Alcance(Escopo.ACADEMIA, List.of(casa), null, casa.getNome());
        }
        return new Alcance(Escopo.CONTA, List.of(), null, CONFIRMACAO_CONTA);
    }

    /** Prévia para a tela: o que some e o que a pessoa precisa digitar. */
    @Transactional(readOnly = true)
    public ExclusaoContaInfo info() {
        Usuario u = eu();
        Alcance a = alcance(u);
        long totalAlunos = 0, outrosUsuarios = 0;
        for (Academia ac : a.academias()) {
            totalAlunos += alunos.countByAcademiaId(ac.getId());
            outrosUsuarios += usuarios.findByAcademiaIdOrderByNomeAsc(ac.getId()).size();
        }
        if (a.escopo() != Escopo.CONTA) outrosUsuarios--; // a própria pessoa
        Rede redeDaPessoa = u.getAcademia().getRede();
        return new ExclusaoContaInfo(a.escopo().name(), a.confirmacao(), u.getAcademia().getNome(),
                redeDaPessoa != null ? redeDaPessoa.getNome() : null, a.academias().size(), totalAlunos,
                Math.max(0, outrosUsuarios), u.doisFatoresAtivo());
    }

    @Transactional
    public void excluir(ExcluirContaRequest req) {
        Usuario u = eu();
        doisFatores.confirmarIdentidade(u, req.senha(), req.codigo());
        Alcance a = alcance(u);
        if (!confere(req.confirmacao(), a.confirmacao())) {
            throw new RegraNegocioException(a.escopo() == Escopo.CONTA
                    ? "Confirmação incorreta: digite EXCLUIR."
                    : "Confirmação incorreta: digite o nome " + (a.escopo() == Escopo.REDE ? "da rede" : "da academia") + ", como aparece no aviso.");
        }
        if (a.escopo() == Escopo.CONTA) {
            remocao.apagarAcessos(u);
            usuarios.delete(u);
            return;
        }
        remocao.apagar(a.academias());
        if (a.rede() != null) {
            remocao.apagarAssinaturaDaRede(a.rede().getId());
            redes.delete(a.rede());
        }
    }

    /** O texto digitado confere com o esperado (sem diferença de maiúsculas, acentos e espaços repetidos)? */
    static boolean confere(String digitado, String esperado) {
        return normalizar(digitado).equals(normalizar(esperado));
    }

    static String normalizar(String s) {
        if (s == null) return "";
        String semAcento = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return semAcento.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
