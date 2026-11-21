package com.academia.service;

import com.academia.model.Academia;
import com.academia.model.Usuario;
import com.academia.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Apaga academias (unidades) por inteiro, com tudo o que há nelas. Usado ao excluir a conta
 * (último administrador ou dono da rede) e ao excluir uma unidade pelo painel da rede.
 * Quem chama confere antes a senha, o código e a confirmação digitada.
 */
@Service
public class RemocaoDeAcademias {

    private final UsuarioRepository usuarios;
    private final AcademiaRepository academias;
    private final SessaoRepository sessoes;
    private final AlunoRepository alunos;
    private final MensalidadeRepository mensalidades;
    private final FichaTreinoRepository fichas;
    private final AvaliacaoFisicaRepository avaliacoes;
    private final ProfessorRepository professores;
    private final PlanoRepository planos;
    private final DispositivosConfiaveis dispositivos;

    public RemocaoDeAcademias(UsuarioRepository usuarios, AcademiaRepository academias, SessaoRepository sessoes,
                              AlunoRepository alunos, MensalidadeRepository mensalidades, FichaTreinoRepository fichas,
                              AvaliacaoFisicaRepository avaliacoes, ProfessorRepository professores, PlanoRepository planos,
                              DispositivosConfiaveis dispositivos) {
        this.usuarios = usuarios;
        this.academias = academias;
        this.sessoes = sessoes;
        this.alunos = alunos;
        this.mensalidades = mensalidades;
        this.fichas = fichas;
        this.avaliacoes = avaliacoes;
        this.professores = professores;
        this.planos = planos;
        this.dispositivos = dispositivos;
    }

    /** Encerra as sessões e esquece os dispositivos lembrados de uma pessoa. */
    public void apagarAcessos(Usuario u) {
        sessoes.apagarDoUsuario(u.getId());
        dispositivos.esquecerTodos(u.getId());
    }

    /**
     * Primeiro os acessos (sessões de todos os usuários e as do dono que estavam com alguma
     * destas unidades aberta, que voltam para a unidade dele), depois os dados.
     */
    @Transactional
    public void apagar(List<Academia> lista) {
        for (Academia ac : lista) {
            sessoes.fecharUnidadeAberta(ac.getId());
            usuarios.findByAcademiaIdOrderByNomeAsc(ac.getId()).forEach(this::apagarAcessos);
        }
        for (Academia ac : lista) apagarDados(ac);
    }

    /** Na ordem das dependências: o que aponta para aluno, professor e plano sai antes deles. */
    private void apagarDados(Academia ac) {
        Long id = ac.getId();
        mensalidades.deleteAll(mensalidades.findByAcademiaIdOrderByDataVencimentoDesc(id));
        avaliacoes.deleteAll(avaliacoes.findByAcademiaIdOrderByDataAvaliacaoDescIdDesc(id));
        fichas.deleteAll(fichas.findByAcademiaIdOrderByDataInicioDesc(id));
        alunos.deleteAll(alunos.findByAcademiaIdOrderByNomeAsc(id));
        professores.deleteAll(professores.findByAcademiaIdOrderByNomeAsc(id));
        planos.deleteAll(planos.findByAcademiaIdOrderByValorMensalAsc(id));
        usuarios.deleteAll(usuarios.findByAcademiaIdOrderByNomeAsc(id));
        academias.delete(ac);
    }
}
