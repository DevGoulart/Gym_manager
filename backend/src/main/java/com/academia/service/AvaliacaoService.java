package com.academia.service;

import com.academia.dto.AvaliacaoRequest;
import com.academia.dto.AvaliacaoResponse;
import com.academia.exception.RecursoNaoEncontradoException;
import com.academia.model.AvaliacaoFisica;
import com.academia.model.Professor;
import com.academia.repository.AvaliacaoFisicaRepository;
import com.academia.security.AcademiaAtual;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AvaliacaoService {

    private final AvaliacaoFisicaRepository avaliacaoRepository;
    private final AlunoService alunoService;
    private final ProfessorService professorService;
    private final AcademiaAtual academiaAtual;

    public AvaliacaoService(AvaliacaoFisicaRepository avaliacaoRepository, AlunoService alunoService,
                            ProfessorService professorService, AcademiaAtual academiaAtual) {
        this.avaliacaoRepository = avaliacaoRepository;
        this.alunoService = alunoService;
        this.professorService = professorService;
        this.academiaAtual = academiaAtual;
    }

    public List<AvaliacaoResponse> listar(Long alunoId) {
        List<AvaliacaoFisica> lista = alunoId == null
                ? avaliacaoRepository.findByAcademiaIdOrderByDataAvaliacaoDescIdDesc(academiaAtual.id())
                : avaliacaoRepository.findByAlunoIdOrderByDataAvaliacaoAscIdAsc(alunoService.buscar(alunoId).getId()).reversed();
        Map<Long, AvaliacaoFisica> anteriores = mapaDeAnteriores();
        return lista.stream().map(a -> toResponse(a, anteriores.get(a.getId()))).toList();
    }

    public AvaliacaoResponse buscarResponse(Long id) {
        AvaliacaoFisica a = buscar(id);
        return toResponse(a, mapaDeAnteriores().get(id));
    }

    private AvaliacaoFisica buscar(Long id) {
        return avaliacaoRepository.findByIdAndAcademiaId(id, academiaAtual.id())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Avaliação " + id + " não encontrada"));
    }

    @Transactional
    public AvaliacaoResponse criar(AvaliacaoRequest req) {
        AvaliacaoFisica a = new AvaliacaoFisica();
        preencher(a, req);
        a = avaliacaoRepository.save(a);
        return buscarResponse(a.getId());
    }

    @Transactional
    public AvaliacaoResponse atualizar(Long id, AvaliacaoRequest req) {
        AvaliacaoFisica a = buscar(id);
        preencher(a, req);
        avaliacaoRepository.save(a);
        return buscarResponse(id);
    }

    @Transactional
    public void excluir(Long id) {
        avaliacaoRepository.delete(buscar(id));
    }

    /** Para cada avaliação, encontra a avaliação imediatamente anterior do mesmo aluno. */
    private Map<Long, AvaliacaoFisica> mapaDeAnteriores() {
        Map<Long, AvaliacaoFisica> ultimaPorAluno = new HashMap<>();
        Map<Long, AvaliacaoFisica> anteriores = new HashMap<>();
        List<AvaliacaoFisica> todas = avaliacaoRepository.findByAcademiaIdOrderByDataAvaliacaoDescIdDesc(academiaAtual.id()).reversed();
        for (AvaliacaoFisica a : todas) {
            Long alunoId = a.getAluno().getId();
            AvaliacaoFisica anterior = ultimaPorAluno.get(alunoId);
            if (anterior != null) anteriores.put(a.getId(), anterior);
            ultimaPorAluno.put(alunoId, a);
        }
        return anteriores;
    }

    private void preencher(AvaliacaoFisica a, AvaliacaoRequest req) {
        double altura = req.altura() > 3 ? req.altura() / 100.0 : req.altura(); // aceita cm ou m
        Professor professor = req.professorId() == null ? null : professorService.buscar(req.professorId());
        a.setAluno(alunoService.buscar(req.alunoId()));
        a.setAcademia(a.getAluno().getAcademia());
        a.setProfessor(professor);
        a.setDataAvaliacao(req.dataAvaliacao() != null ? req.dataAvaliacao()
                : (a.getDataAvaliacao() != null ? a.getDataAvaliacao() : LocalDate.now()));
        a.setPeso(req.peso());
        a.setAltura(altura);
        a.setPercentualGordura(req.percentualGordura());
        a.setCintura(req.cintura());
        a.setQuadril(req.quadril());
        a.setBraco(req.braco());
        a.setCoxa(req.coxa());
        a.setObservacoes(Textos.limpar(req.observacoes()));
    }

    private static Double diferenca(Double atual, Double anterior) {
        if (atual == null || anterior == null) return null;
        return Math.round((atual - anterior) * 100.0) / 100.0;
    }

    private static AvaliacaoResponse toResponse(AvaliacaoFisica a, AvaliacaoFisica anterior) {
        Professor p = a.getProfessor();
        return new AvaliacaoResponse(a.getId(), a.getAluno().getId(), a.getAluno().getNome(),
                p == null ? null : p.getId(), p == null ? null : p.getNome(),
                a.getDataAvaliacao(), a.getPeso(), a.getAltura(), a.getPercentualGordura(),
                a.getCintura(), a.getQuadril(), a.getBraco(), a.getCoxa(), a.getObservacoes(),
                a.getImc(), a.getClassificacaoImc(),
                anterior == null ? null : diferenca(a.getPeso(), anterior.getPeso()),
                anterior == null ? null : diferenca(a.getPercentualGordura(), anterior.getPercentualGordura()));
    }
}
