package com.academia.service;

import com.academia.dto.AlunoRequest;
import com.academia.dto.AlunoResponse;
import com.academia.exception.RecursoNaoEncontradoException;
import com.academia.exception.RegraNegocioException;
import com.academia.model.Aluno;
import com.academia.model.Mensalidade;
import com.academia.model.Plano;
import com.academia.model.StatusMensalidade;
import com.academia.repository.*;
import com.academia.security.AcademiaAtual;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class AlunoService {

    private final AlunoRepository alunoRepository;
    private final PlanoService planoService;
    private final MensalidadeRepository mensalidadeRepository;
    private final FichaTreinoRepository fichaTreinoRepository;
    private final AvaliacaoFisicaRepository avaliacaoRepository;
    private final AcademiaAtual academiaAtual;
    private final AssinaturaService assinaturas;

    public AlunoService(AlunoRepository alunoRepository,
                        PlanoService planoService,
                        MensalidadeRepository mensalidadeRepository,
                        FichaTreinoRepository fichaTreinoRepository,
                        AvaliacaoFisicaRepository avaliacaoRepository,
                        AcademiaAtual academiaAtual, AssinaturaService assinaturas) {
        this.assinaturas = assinaturas;
        this.alunoRepository = alunoRepository;
        this.planoService = planoService;
        this.mensalidadeRepository = mensalidadeRepository;
        this.fichaTreinoRepository = fichaTreinoRepository;
        this.avaliacaoRepository = avaliacaoRepository;
        this.academiaAtual = academiaAtual;
    }

    /** Consulta de alunos da academia logada. A busca procura no nome ou no CPF. */
    public List<AlunoResponse> listar(String busca) {
        String termo = Textos.limpar(busca);
        String termoMinusculo = termo == null ? null : termo.toLowerCase();
        return alunoRepository.findByAcademiaIdOrderByNomeAsc(academiaAtual.id()).stream()
                .filter(a -> termo == null || a.getNome().toLowerCase().contains(termoMinusculo)
                        || (a.getCpf() != null && a.getCpf().contains(termo)))
                .map(this::toResponse).toList();
    }

    public AlunoResponse buscarResponse(Long id) {
        return toResponse(buscar(id));
    }

    public Aluno buscar(Long id) {
        // Só encontra alunos da academia logada: o id de outra academia responde 404
        return alunoRepository.findByIdAndAcademiaId(id, academiaAtual.id())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Aluno " + id + " não encontrado"));
    }

    @Transactional
    public AlunoResponse criar(AlunoRequest req) {
        String cpf = Textos.limpar(req.cpf());
        if (cpf != null && alunoRepository.existsByAcademiaIdAndCpf(academiaAtual.id(), cpf)) {
            throw new RegraNegocioException("Já existe um aluno com o CPF " + cpf);
        }
        // limites do plano (aluno ativo ocupa vaga) e assinatura vencida
        if (req.ativo() == null || req.ativo()) assinaturas.exigirVagaAluno(academiaAtual.id());
        else assinaturas.exigirCadastroLiberado(academiaAtual.id());
        Aluno aluno = new Aluno();
        aluno.setAcademia(academiaAtual.referencia());
        preencher(aluno, req);
        if (aluno.getPlano() != null && !aluno.getPlano().isAtivo()) {
            throw new RegraNegocioException("O plano escolhido está desativado");
        }
        aluno = alunoRepository.save(aluno);

        if (aluno.getPlano() != null) {
            Mensalidade m = new Mensalidade();
            m.setAcademia(aluno.getAcademia());
            m.setAluno(aluno);
            m.setValor(aluno.getPlano().getValorMensal());
            m.setDataVencimento(aluno.getDataMatricula());
            m.setReferencia(MensalidadeService.referenciaDe(aluno.getDataMatricula()));
            mensalidadeRepository.save(m);
        }
        return toResponse(aluno);
    }

    @Transactional
    public AlunoResponse atualizar(Long id, AlunoRequest req) {
        Aluno aluno = buscar(id);
        String cpf = Textos.limpar(req.cpf());
        if (cpf != null && alunoRepository.existsByAcademiaIdAndCpfAndIdNot(academiaAtual.id(), cpf, id)) {
            throw new RegraNegocioException("Já existe outro aluno com o CPF " + cpf);
        }
        // reativar um aluno ocupa uma vaga do plano
        if (!aluno.isAtivo() && (req.ativo() == null || req.ativo())) assinaturas.exigirVagaAluno(academiaAtual.id());
        preencher(aluno, req);
        return toResponse(alunoRepository.save(aluno));
    }

    @Transactional
    public void excluir(Long id) {
        Aluno aluno = buscar(id);
        mensalidadeRepository.deleteByAlunoId(id);
        fichaTreinoRepository.deleteAll(fichaTreinoRepository.findByAlunoId(id));
        avaliacaoRepository.deleteAll(avaliacaoRepository.findByAlunoId(id));
        alunoRepository.delete(aluno);
    }

    private void preencher(Aluno aluno, AlunoRequest req) {
        aluno.setNome(req.nome().trim());
        aluno.setCpf(Textos.limpar(req.cpf()));
        aluno.setEmail(Textos.limpar(req.email()));
        aluno.setTelefone(Textos.limpar(req.telefone()));
        aluno.setDataNascimento(req.dataNascimento());
        if (req.dataMatricula() != null) {
            aluno.setDataMatricula(req.dataMatricula());
        } else if (aluno.getDataMatricula() == null) {
            aluno.setDataMatricula(LocalDate.now());
        }
        Plano plano = req.planoId() == null ? null : planoService.buscar(req.planoId());
        aluno.setPlano(plano);
        aluno.setAtivo(req.ativo() == null || req.ativo());
    }

    AlunoResponse toResponse(Aluno a) {
        List<Mensalidade> mensalidades = mensalidadeRepository.findByAlunoIdOrderByDataVencimentoDesc(a.getId());
        String situacao = "EM_DIA";
        if (mensalidades.stream().anyMatch(m -> m.getStatus() == StatusMensalidade.ATRASADA)) {
            situacao = "ATRASADO";
        } else if (mensalidades.stream().anyMatch(m -> m.getStatus() == StatusMensalidade.PENDENTE
                && !m.getDataVencimento().isAfter(LocalDate.now().plusDays(7)))) {
            situacao = "PENDENTE";
        }
        Plano p = a.getPlano();
        return new AlunoResponse(a.getId(), a.getNome(), a.getCpf(), a.getEmail(), a.getTelefone(),
                a.getDataNascimento(), a.getDataMatricula(),
                p == null ? null : p.getId(), p == null ? null : p.getNome(),
                a.isAtivo(), situacao);
    }
}
