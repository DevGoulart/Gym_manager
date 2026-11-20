package com.academia.service;

import com.academia.dto.ProfessorRequest;
import com.academia.exception.RecursoNaoEncontradoException;
import com.academia.exception.RegraNegocioException;
import com.academia.model.Professor;
import com.academia.repository.AvaliacaoFisicaRepository;
import com.academia.repository.FichaTreinoRepository;
import com.academia.repository.ProfessorRepository;
import com.academia.security.AcademiaAtual;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProfessorService {

    private final ProfessorRepository professorRepository;
    private final FichaTreinoRepository fichaTreinoRepository;
    private final AvaliacaoFisicaRepository avaliacaoRepository;
    private final AcademiaAtual academiaAtual;

    public ProfessorService(ProfessorRepository professorRepository,
                            FichaTreinoRepository fichaTreinoRepository,
                            AvaliacaoFisicaRepository avaliacaoRepository,
                            AcademiaAtual academiaAtual) {
        this.professorRepository = professorRepository;
        this.fichaTreinoRepository = fichaTreinoRepository;
        this.avaliacaoRepository = avaliacaoRepository;
        this.academiaAtual = academiaAtual;
    }

    public List<Professor> listar(String busca) {
        String termo = Textos.limpar(busca);
        List<Professor> todos = professorRepository.findByAcademiaIdOrderByNomeAsc(academiaAtual.id());
        if (termo == null) return todos;
        String t = termo.toLowerCase();
        return todos.stream().filter(p -> p.getNome().toLowerCase().contains(t)).toList();
    }

    public Professor buscar(Long id) {
        return professorRepository.findByIdAndAcademiaId(id, academiaAtual.id())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Professor " + id + " não encontrado"));
    }

    @Transactional
    public Professor criar(ProfessorRequest req) {
        String cpf = Textos.limpar(req.cpf());
        if (cpf != null && professorRepository.existsByAcademiaIdAndCpf(academiaAtual.id(), cpf)) {
            throw new RegraNegocioException("Já existe um professor com o CPF " + cpf);
        }
        Professor p = new Professor();
        p.setAcademia(academiaAtual.referencia());
        preencher(p, req);
        return professorRepository.save(p);
    }

    @Transactional
    public Professor atualizar(Long id, ProfessorRequest req) {
        Professor p = buscar(id);
        String cpf = Textos.limpar(req.cpf());
        if (cpf != null && professorRepository.existsByAcademiaIdAndCpfAndIdNot(academiaAtual.id(), cpf, id)) {
            throw new RegraNegocioException("Já existe outro professor com o CPF " + cpf);
        }
        preencher(p, req);
        return professorRepository.save(p);
    }

    @Transactional
    public void excluir(Long id) {
        Professor p = buscar(id);
        fichaTreinoRepository.findByProfessorId(id).forEach(f -> f.setProfessor(null));
        avaliacaoRepository.findByProfessorId(id).forEach(a -> a.setProfessor(null));
        professorRepository.delete(p);
    }

    private void preencher(Professor p, ProfessorRequest req) {
        p.setNome(req.nome().trim());
        p.setCpf(Textos.limpar(req.cpf()));
        p.setEmail(Textos.limpar(req.email()));
        p.setTelefone(Textos.limpar(req.telefone()));
        p.setEspecialidade(Textos.limpar(req.especialidade()));
        p.setCref(Textos.limpar(req.cref()));
    }
}
