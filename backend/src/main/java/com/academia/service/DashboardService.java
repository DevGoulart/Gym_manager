package com.academia.service;

import com.academia.dto.DashboardResponse;
import com.academia.model.Mensalidade;
import com.academia.model.StatusMensalidade;
import com.academia.repository.*;
import com.academia.security.AcademiaAtual;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class DashboardService {

    private final AlunoRepository alunoRepository;
    private final ProfessorRepository professorRepository;
    private final PlanoRepository planoRepository;
    private final MensalidadeRepository mensalidadeRepository;
    private final FichaTreinoRepository fichaRepository;
    private final AvaliacaoFisicaRepository avaliacaoRepository;
    private final AcademiaAtual academiaAtual;

    public DashboardService(AlunoRepository alunoRepository, ProfessorRepository professorRepository,
                            PlanoRepository planoRepository, MensalidadeRepository mensalidadeRepository,
                            FichaTreinoRepository fichaRepository, AvaliacaoFisicaRepository avaliacaoRepository,
                            AcademiaAtual academiaAtual) {
        this.alunoRepository = alunoRepository;
        this.professorRepository = professorRepository;
        this.planoRepository = planoRepository;
        this.mensalidadeRepository = mensalidadeRepository;
        this.fichaRepository = fichaRepository;
        this.avaliacaoRepository = avaliacaoRepository;
        this.academiaAtual = academiaAtual;
    }

    public DashboardResponse resumo() {
        Long academiaId = academiaAtual.id();
        List<Mensalidade> mensalidades = mensalidadeRepository.findByAcademiaIdOrderByDataVencimentoDesc(academiaId);
        LocalDate hoje = LocalDate.now();

        long pendentes = mensalidades.stream().filter(m -> m.getStatus() == StatusMensalidade.PENDENTE).count();
        long atrasadas = mensalidades.stream().filter(m -> m.getStatus() == StatusMensalidade.ATRASADA).count();
        BigDecimal recebidoNoMes = mensalidades.stream()
                .filter(m -> m.getDataPagamento() != null
                        && m.getDataPagamento().getMonth() == hoje.getMonth()
                        && m.getDataPagamento().getYear() == hoje.getYear())
                .map(Mensalidade::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal emAberto = mensalidades.stream()
                .filter(m -> m.getStatus() != StatusMensalidade.PAGA)
                .map(Mensalidade::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new DashboardResponse(
                alunoRepository.countByAcademiaIdAndAtivoTrue(academiaId),
                alunoRepository.countByAcademiaId(academiaId),
                professorRepository.countByAcademiaId(academiaId),
                planoRepository.countByAcademiaIdAndAtivoTrue(academiaId),
                pendentes,
                atrasadas,
                recebidoNoMes,
                emAberto,
                fichaRepository.countByAcademiaId(academiaId),
                avaliacaoRepository.countByAcademiaId(academiaId));
    }
}
