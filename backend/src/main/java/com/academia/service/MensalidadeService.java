package com.academia.service;

import com.academia.dto.GerarMensalidadesRequest;
import com.academia.dto.MensalidadeRequest;
import com.academia.dto.MensalidadeResponse;
import com.academia.dto.PagamentoRequest;
import com.academia.exception.RecursoNaoEncontradoException;
import com.academia.exception.RegraNegocioException;
import com.academia.model.Aluno;
import com.academia.model.Mensalidade;
import com.academia.model.StatusMensalidade;
import com.academia.repository.AlunoRepository;
import com.academia.repository.MensalidadeRepository;
import com.academia.security.AcademiaAtual;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class MensalidadeService {

    private static final DateTimeFormatter REFERENCIA = DateTimeFormatter.ofPattern("MM/yyyy");

    private final MensalidadeRepository mensalidadeRepository;
    private final AlunoRepository alunoRepository;
    private final AcademiaAtual academiaAtual;

    public MensalidadeService(MensalidadeRepository mensalidadeRepository, AlunoRepository alunoRepository,
                              AcademiaAtual academiaAtual) {
        this.mensalidadeRepository = mensalidadeRepository;
        this.alunoRepository = alunoRepository;
        this.academiaAtual = academiaAtual;
    }

    /** Busca o aluno só dentro da academia logada. */
    private Aluno alunoDaAcademia(Long alunoId) {
        return alunoRepository.findByIdAndAcademiaId(alunoId, academiaAtual.id())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Aluno " + alunoId + " não encontrado"));
    }

    static String referenciaDe(LocalDate data) {
        return data.format(REFERENCIA);
    }

    public List<MensalidadeResponse> listar(Long alunoId, StatusMensalidade status) {
        List<Mensalidade> lista = alunoId == null
                ? mensalidadeRepository.findByAcademiaIdOrderByDataVencimentoDesc(academiaAtual.id())
                : mensalidadeRepository.findByAlunoIdOrderByDataVencimentoDesc(alunoDaAcademia(alunoId).getId());
        return lista.stream()
                .filter(m -> status == null || m.getStatus() == status)
                .map(MensalidadeService::toResponse)
                .toList();
    }

    public MensalidadeResponse buscarResponse(Long id) {
        return toResponse(buscar(id));
    }

    private Mensalidade buscar(Long id) {
        return mensalidadeRepository.findByIdAndAcademiaId(id, academiaAtual.id())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Mensalidade " + id + " não encontrada"));
    }

    @Transactional
    public MensalidadeResponse criar(MensalidadeRequest req) {
        Mensalidade m = new Mensalidade();
        preencher(m, req);
        return toResponse(mensalidadeRepository.save(m));
    }

    @Transactional
    public MensalidadeResponse atualizar(Long id, MensalidadeRequest req) {
        Mensalidade m = buscar(id);
        preencher(m, req);
        return toResponse(mensalidadeRepository.save(m));
    }

    @Transactional
    public void excluir(Long id) {
        mensalidadeRepository.delete(buscar(id));
    }

    @Transactional
    public MensalidadeResponse pagar(Long id, PagamentoRequest req) {
        Mensalidade m = buscar(id);
        if (m.getStatus() == StatusMensalidade.PAGA) {
            throw new RegraNegocioException("Esta mensalidade já foi paga em " + m.getDataPagamento());
        }
        m.setDataPagamento(req != null && req.dataPagamento() != null ? req.dataPagamento() : LocalDate.now());
        String forma = req == null ? null : Textos.limpar(req.formaPagamento());
        m.setFormaPagamento(forma == null ? "Dinheiro" : forma);
        return toResponse(mensalidadeRepository.save(m));
    }

    @Transactional
    public MensalidadeResponse estornar(Long id) {
        Mensalidade m = buscar(id);
        if (m.getStatus() != StatusMensalidade.PAGA) {
            throw new RegraNegocioException("Esta mensalidade não está paga");
        }
        m.setDataPagamento(null);
        m.setFormaPagamento(null);
        return toResponse(mensalidadeRepository.save(m));
    }

    /**
     * Gera as próximas N mensalidades do aluno, uma por mês, a partir do mês seguinte
     * à última mensalidade existente (ou da matrícula, se ainda não houver nenhuma).
     */
    @Transactional
    public List<MensalidadeResponse> gerar(GerarMensalidadesRequest req) {
        Aluno aluno = alunoDaAcademia(req.alunoId());
        if (aluno.getPlano() == null) {
            throw new RegraNegocioException("O aluno não possui plano. Vincule um plano antes de gerar mensalidades.");
        }
        int meses = req.meses() == null ? 1 : req.meses();
        List<Mensalidade> existentes = mensalidadeRepository.findByAlunoIdOrderByDataVencimentoDesc(aluno.getId());
        LocalDate proximo = existentes.isEmpty()
                ? aluno.getDataMatricula()
                : existentes.get(0).getDataVencimento().plusMonths(1);

        List<MensalidadeResponse> criadas = new ArrayList<>();
        for (int i = 0; i < meses; i++) {
            LocalDate venc = proximo.plusMonths(i);
            String ref = referenciaDe(venc);
            if (mensalidadeRepository.existsByAlunoIdAndReferencia(aluno.getId(), ref)) continue;
            Mensalidade m = new Mensalidade();
            m.setAcademia(aluno.getAcademia());
            m.setAluno(aluno);
            m.setReferencia(ref);
            m.setValor(aluno.getPlano().getValorMensal());
            m.setDataVencimento(venc);
            criadas.add(toResponse(mensalidadeRepository.save(m)));
        }
        return criadas;
    }

    private void preencher(Mensalidade m, MensalidadeRequest req) {
        Aluno aluno = alunoDaAcademia(req.alunoId());
        BigDecimal valor = req.valor();
        if (valor == null) {
            if (aluno.getPlano() == null) {
                throw new RegraNegocioException("Informe o valor: o aluno não possui plano");
            }
            valor = aluno.getPlano().getValorMensal();
        }
        String ref = Textos.limpar(req.referencia());
        m.setAluno(aluno);
        m.setAcademia(aluno.getAcademia());
        m.setValor(valor);
        m.setDataVencimento(req.dataVencimento());
        m.setReferencia(ref == null ? referenciaDe(req.dataVencimento()) : ref);
        m.setDataPagamento(req.dataPagamento());
        m.setFormaPagamento(req.dataPagamento() == null ? null : Textos.limpar(req.formaPagamento()));
    }

    static MensalidadeResponse toResponse(Mensalidade m) {
        return new MensalidadeResponse(m.getId(), m.getAluno().getId(), m.getAluno().getNome(),
                m.getReferencia(), m.getValor(), m.getDataVencimento(), m.getDataPagamento(),
                m.getFormaPagamento(), m.getStatus());
    }
}
