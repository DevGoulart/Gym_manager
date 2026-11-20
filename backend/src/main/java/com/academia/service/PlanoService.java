package com.academia.service;

import com.academia.dto.PlanoRequest;
import com.academia.exception.RecursoNaoEncontradoException;
import com.academia.exception.RegraNegocioException;
import com.academia.model.Plano;
import com.academia.repository.AlunoRepository;
import com.academia.repository.PlanoRepository;
import com.academia.security.AcademiaAtual;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PlanoService {

    private final PlanoRepository planoRepository;
    private final AlunoRepository alunoRepository;
    private final AcademiaAtual academiaAtual;

    public PlanoService(PlanoRepository planoRepository, AlunoRepository alunoRepository, AcademiaAtual academiaAtual) {
        this.planoRepository = planoRepository;
        this.alunoRepository = alunoRepository;
        this.academiaAtual = academiaAtual;
    }

    public List<Plano> listar() {
        return planoRepository.findByAcademiaIdOrderByValorMensalAsc(academiaAtual.id());
    }

    public Plano buscar(Long id) {
        return planoRepository.findByIdAndAcademiaId(id, academiaAtual.id())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Plano " + id + " não encontrado"));
    }

    @Transactional
    public Plano criar(PlanoRequest req) {
        Plano plano = new Plano();
        plano.setAcademia(academiaAtual.referencia());
        preencher(plano, req);
        return planoRepository.save(plano);
    }

    @Transactional
    public Plano atualizar(Long id, PlanoRequest req) {
        Plano plano = buscar(id);
        preencher(plano, req);
        return planoRepository.save(plano);
    }

    @Transactional
    public void excluir(Long id) {
        Plano plano = buscar(id);
        if (alunoRepository.existsByPlanoId(id)) {
            throw new RegraNegocioException("Este plano possui alunos vinculados. Desative-o em vez de excluir.");
        }
        planoRepository.delete(plano);
    }

    private void preencher(Plano plano, PlanoRequest req) {
        plano.setNome(req.nome().trim());
        plano.setDescricao(Textos.limpar(req.descricao()));
        plano.setValorMensal(req.valorMensal());
        plano.setDuracaoMeses(req.duracaoMeses());
        plano.setAtivo(req.ativo() == null || req.ativo());
    }
}
