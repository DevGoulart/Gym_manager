package com.academia.service;

import com.academia.dto.ExercicioDTO;
import com.academia.dto.TreinoRequest;
import com.academia.dto.TreinoResponse;
import com.academia.exception.RecursoNaoEncontradoException;
import com.academia.exception.RegraNegocioException;
import com.academia.model.Exercicio;
import com.academia.model.FichaTreino;
import com.academia.model.Professor;
import com.academia.repository.FichaTreinoRepository;
import com.academia.security.AcademiaAtual;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class TreinoService {

    private final FichaTreinoRepository fichaRepository;
    private final AlunoService alunoService;
    private final ProfessorService professorService;
    private final AcademiaAtual academiaAtual;

    public TreinoService(FichaTreinoRepository fichaRepository, AlunoService alunoService,
                         ProfessorService professorService, AcademiaAtual academiaAtual) {
        this.fichaRepository = fichaRepository;
        this.alunoService = alunoService;
        this.professorService = professorService;
        this.academiaAtual = academiaAtual;
    }

    public List<TreinoResponse> listar(Long alunoId) {
        List<FichaTreino> fichas = alunoId == null
                ? fichaRepository.findByAcademiaIdOrderByDataInicioDesc(academiaAtual.id())
                : fichaRepository.findByAlunoIdOrderByDataInicioDesc(alunoService.buscar(alunoId).getId());
        return fichas.stream().map(TreinoService::toResponse).toList();
    }

    public TreinoResponse buscarResponse(Long id) {
        return toResponse(buscar(id));
    }

    private FichaTreino buscar(Long id) {
        return fichaRepository.findByIdAndAcademiaId(id, academiaAtual.id())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Ficha de treino " + id + " não encontrada"));
    }

    @Transactional
    public TreinoResponse criar(TreinoRequest req) {
        FichaTreino f = new FichaTreino();
        preencher(f, req);
        return toResponse(fichaRepository.save(f));
    }

    @Transactional
    public TreinoResponse atualizar(Long id, TreinoRequest req) {
        FichaTreino f = buscar(id);
        preencher(f, req);
        return toResponse(fichaRepository.save(f));
    }

    @Transactional
    public void excluir(Long id) {
        fichaRepository.delete(buscar(id));
    }

    private void preencher(FichaTreino f, TreinoRequest req) {
        LocalDate inicio = req.dataInicio() != null ? req.dataInicio()
                : (f.getDataInicio() != null ? f.getDataInicio() : LocalDate.now());
        if (req.dataFim() != null && req.dataFim().isBefore(inicio)) {
            throw new RegraNegocioException("A data de fim não pode ser anterior à data de início");
        }
        Professor professor = req.professorId() == null ? null : professorService.buscar(req.professorId());
        f.setAluno(alunoService.buscar(req.alunoId()));
        f.setAcademia(f.getAluno().getAcademia());
        f.setProfessor(professor);
        f.setNome(req.nome().trim());
        f.setObjetivo(Textos.limpar(req.objetivo()));
        f.setDataInicio(inicio);
        f.setDataFim(req.dataFim());
        f.setObservacoes(Textos.limpar(req.observacoes()));

        List<Exercicio> exercicios = new ArrayList<>();
        if (req.exercicios() != null) {
            for (ExercicioDTO e : req.exercicios()) {
                exercicios.add(new Exercicio(e.nome().trim(), e.series(),
                        Textos.limpar(e.repeticoes()), Textos.limpar(e.carga())));
            }
        }
        f.getExercicios().clear();
        f.getExercicios().addAll(exercicios);
    }

    static TreinoResponse toResponse(FichaTreino f) {
        LocalDate hoje = LocalDate.now();
        boolean vigente = !f.getDataInicio().isAfter(hoje) && (f.getDataFim() == null || !f.getDataFim().isBefore(hoje));
        List<ExercicioDTO> exercicios = f.getExercicios().stream()
                .map(e -> new ExercicioDTO(e.getNome(), e.getSeries(), e.getRepeticoes(), e.getCarga()))
                .toList();
        Professor p = f.getProfessor();
        return new TreinoResponse(f.getId(), f.getAluno().getId(), f.getAluno().getNome(),
                p == null ? null : p.getId(), p == null ? null : p.getNome(),
                f.getNome(), f.getObjetivo(), f.getDataInicio(), f.getDataFim(), f.getObservacoes(),
                exercicios, vigente);
    }
}
