package com.academia.app;

import com.academia.app.Campo.Tipo;

import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.academia.app.Formatos.data;
import static com.academia.app.Formatos.dinheiro;
import static com.academia.app.Formatos.numero;
import static com.academia.app.Formatos.tem;
import static com.academia.app.Formatos.txt;
import static com.academia.app.Formatos.variacao;

/** Registro dos 6 módulos do sistema. */
public final class Modulos {

    public static final int VERDE = 0xFF2F9E44;
    public static final int LARANJA = 0xFFF08C00;
    public static final int VERMELHO = 0xFFE03131;
    public static final int CINZA = 0xFF868E96;
    public static final int AZUL = 0xFF1C7ED6;

    public static final String[] FORMAS_PAGAMENTO = {"PIX", "Dinheiro", "Cartão de crédito", "Cartão de débito", "Boleto"};

    private static final Map<String, Modulo> TODOS = new LinkedHashMap<>();

    static {
        registrar(alunos());
        registrar(planos());
        registrar(mensalidades());
        registrar(professores());
        registrar(treinos());
        registrar(avaliacoes());
    }

    private Modulos() {
    }

    private static void registrar(Modulo m) {
        TODOS.put(m.id, m);
    }

    public static Modulo get(String id) {
        return TODOS.get(id);
    }

    public static Iterable<Modulo> todos() {
        return TODOS.values();
    }

    private static String juntar(String sep, String... partes) {
        StringBuilder sb = new StringBuilder();
        for (String p : partes) {
            if (p == null || p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(sep);
            sb.append(p);
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ ALUNOS
    private static Modulo alunos() {
        Modulo m = new Modulo("alunos", "Alunos", "Aluno", "👤", "/api/alunos", 0xFF1C7ED6)
                .campo(new Campo("nome", "Nome completo", Tipo.TEXTO).obrigatorio())
                .campo(new Campo("cpf", "CPF", Tipo.TEXTO).dica("000.000.000-00"))
                .campo(new Campo("email", "E-mail", Tipo.EMAIL))
                .campo(new Campo("telefone", "Telefone", Tipo.TELEFONE))
                .campo(new Campo("dataNascimento", "Data de nascimento", Tipo.DATA))
                .campo(new Campo("dataMatricula", "Data da matrícula", Tipo.DATA).padrao("HOJE"))
                .campo(new Campo("planoId", "Plano", Tipo.SELECAO_API).endpoint("/api/planos"))
                .campo(new Campo("ativo", "Aluno ativo", Tipo.BOOLEANO).padrao(true));
        m.buscaNoServidor = true;
        m.subtituloItem = o -> juntar("\n",
                juntar(" • ", tem(o, "planoNome") ? "Plano " + txt(o, "planoNome") : "Sem plano",
                        tem(o, "cpf") ? "CPF " + txt(o, "cpf") : ""),
                txt(o, "telefone"));
        m.seloItem = o -> {
            if (!o.optBoolean("ativo", true)) return new Modulo.Selo("INATIVO", CINZA);
            switch (txt(o, "situacaoFinanceira")) {
                case "ATRASADO": return new Modulo.Selo("ATRASADO", VERMELHO);
                case "PENDENTE": return new Modulo.Selo("A VENCER", LARANJA);
                default: return new Modulo.Selo("EM DIA", VERDE);
            }
        };
        m.resumo = o -> {
            String sit;
            switch (txt(o, "situacaoFinanceira")) {
                case "ATRASADO": sit = "🔴 Possui mensalidade atrasada"; break;
                case "PENDENTE": sit = "🟠 Mensalidade vencendo nos próximos dias"; break;
                default: sit = "🟢 Mensalidades em dia";
            }
            return "Situação financeira: " + sit + "\nMatriculado em " + data(txt(o, "dataMatricula"))
                    + (tem(o, "planoNome") ? "\nPlano: " + txt(o, "planoNome") : "\nSem plano vinculado");
        };
        return m;
    }

    // ------------------------------------------------------------------ PLANOS
    private static Modulo planos() {
        Modulo m = new Modulo("planos", "Planos", "Plano", "📋", "/api/planos", 0xFF7048E8)
                .campo(new Campo("nome", "Nome do plano", Tipo.TEXTO).obrigatorio().dica("Ex.: Mensal, Trimestral"))
                .campo(new Campo("descricao", "Descrição / benefícios", Tipo.MULTILINHA))
                .campo(new Campo("valorMensal", "Valor mensal (R$)", Tipo.DECIMAL).obrigatorio())
                .campo(new Campo("duracaoMeses", "Duração (meses)", Tipo.NUMERO).obrigatorio().padrao(1))
                .campo(new Campo("ativo", "Plano ativo (disponível para novas matrículas)", Tipo.BOOLEANO).padrao(true));
        m.subtituloItem = o -> {
            int meses = o.optInt("duracaoMeses", 1);
            return juntar("\n",
                    dinheiro(o.optDouble("valorMensal", 0)) + "/mês • " + meses + (meses == 1 ? " mês" : " meses"),
                    txt(o, "descricao"));
        };
        m.seloItem = o -> o.optBoolean("ativo", true)
                ? new Modulo.Selo("ATIVO", VERDE) : new Modulo.Selo("INATIVO", CINZA);
        return m;
    }

    // ------------------------------------------------------------ MENSALIDADES
    private static Modulo mensalidades() {
        Modulo m = new Modulo("mensalidades", "Mensalidades", "Mensalidade", "💰", "/api/mensalidades", 0xFF2F9E44)
                .campo(new Campo("alunoId", "Aluno", Tipo.SELECAO_API).obrigatorio().endpoint("/api/alunos"))
                .campo(new Campo("dataVencimento", "Vencimento", Tipo.DATA).obrigatorio().padrao("HOJE"))
                .campo(new Campo("valor", "Valor (R$)", Tipo.DECIMAL).dica("Vazio = valor do plano do aluno"))
                .campo(new Campo("referencia", "Mês de referência", Tipo.TEXTO).dica("MM/AAAA — vazio = mês do vencimento"))
                .campo(new Campo("dataPagamento", "Data do pagamento", Tipo.DATA).dica("Deixe vazio se ainda não foi paga"))
                .campo(new Campo("formaPagamento", "Forma de pagamento", Tipo.SELECAO_FIXA).opcoes(FORMAS_PAGAMENTO));
        m.filtrosStatus = new String[][]{
                {"Todas", ""}, {"Atrasadas", "ATRASADA"}, {"Pendentes", "PENDENTE"}, {"Pagas", "PAGA"}};
        m.tituloItem = o -> txt(o, "alunoNome");
        m.subtituloItem = o -> {
            String linha1 = "Ref. " + txt(o, "referencia") + " • " + dinheiro(o.optDouble("valor", 0));
            String linha2 = tem(o, "dataPagamento")
                    ? "Paga em " + data(txt(o, "dataPagamento")) + (tem(o, "formaPagamento") ? " via " + txt(o, "formaPagamento") : "")
                    : "Vence em " + data(txt(o, "dataVencimento"));
            return linha1 + "\n" + linha2;
        };
        m.seloItem = o -> {
            switch (txt(o, "status")) {
                case "PAGA": return new Modulo.Selo("PAGA", VERDE);
                case "ATRASADA": return new Modulo.Selo("ATRASADA", VERMELHO);
                default: return new Modulo.Selo("PENDENTE", LARANJA);
            }
        };
        m.resumo = o -> {
            String st = txt(o, "status");
            String s = "PAGA".equals(st) ? "🟢 Paga em " + data(txt(o, "dataPagamento"))
                    + (tem(o, "formaPagamento") ? " (" + txt(o, "formaPagamento") + ")" : "")
                    : "ATRASADA".equals(st) ? "🔴 Atrasada — venceu em " + data(txt(o, "dataVencimento"))
                    : "🟠 Pendente — vence em " + data(txt(o, "dataVencimento"));
            return "Situação: " + s + "\nValor: " + dinheiro(o.optDouble("valor", 0));
        };
        return m;
    }

    // ------------------------------------------------------------- PROFESSORES
    private static Modulo professores() {
        Modulo m = new Modulo("professores", "Professores", "Professor", "🧑‍🏫", "/api/professores", 0xFFE8590C)
                .campo(new Campo("nome", "Nome completo", Tipo.TEXTO).obrigatorio())
                .campo(new Campo("cpf", "CPF", Tipo.TEXTO))
                .campo(new Campo("cref", "CREF", Tipo.TEXTO).dica("Ex.: 012345-G/SP"))
                .campo(new Campo("especialidade", "Especialidade", Tipo.TEXTO).dica("Ex.: Musculação, Pilates"))
                .campo(new Campo("email", "E-mail", Tipo.EMAIL))
                .campo(new Campo("telefone", "Telefone", Tipo.TELEFONE));
        m.buscaNoServidor = true;
        m.subtituloItem = o -> juntar("\n",
                juntar(" • ", txt(o, "especialidade"), tem(o, "cref") ? "CREF " + txt(o, "cref") : ""),
                juntar(" • ", txt(o, "telefone"), txt(o, "email")));
        return m;
    }

    // ----------------------------------------------------------------- TREINOS
    private static Modulo treinos() {
        Modulo m = new Modulo("treinos", "Treinos", "Ficha de treino", "🏋️", "/api/treinos", 0xFFD6336C)
                .campo(new Campo("alunoId", "Aluno", Tipo.SELECAO_API).obrigatorio().endpoint("/api/alunos"))
                .campo(new Campo("professorId", "Professor responsável", Tipo.SELECAO_API).endpoint("/api/professores"))
                .campo(new Campo("nome", "Nome da ficha", Tipo.TEXTO).obrigatorio().dica("Ex.: Treino A - Peito e Tríceps"))
                .campo(new Campo("objetivo", "Objetivo", Tipo.SELECAO_FIXA)
                        .opcoes("Hipertrofia", "Emagrecimento", "Condicionamento", "Força", "Resistência", "Reabilitação"))
                .campo(new Campo("dataInicio", "Início", Tipo.DATA).padrao("HOJE"))
                .campo(new Campo("dataFim", "Fim (validade da ficha)", Tipo.DATA))
                .campo(new Campo("observacoes", "Observações", Tipo.MULTILINHA))
                .campo(new Campo("exercicios", "Exercícios", Tipo.EXERCICIOS));
        m.subtituloItem = o -> {
            int n = o.optJSONArray("exercicios") == null ? 0 : o.optJSONArray("exercicios").length();
            return juntar("\n",
                    juntar(" • ", txt(o, "alunoNome"), tem(o, "professorNome") ? "Prof. " + txt(o, "professorNome") : ""),
                    juntar(" • ", n + (n == 1 ? " exercício" : " exercícios"), txt(o, "objetivo")));
        };
        m.seloItem = o -> o.optBoolean("vigente")
                ? new Modulo.Selo("VIGENTE", VERDE) : new Modulo.Selo("ENCERRADA", CINZA);
        m.resumo = o -> "Aluno: " + txt(o, "alunoNome")
                + (o.optBoolean("vigente") ? "\n🟢 Ficha vigente" : "\n⚪ Ficha fora do período de validade");
        return m;
    }

    // -------------------------------------------------------------- AVALIAÇÕES
    private static Modulo avaliacoes() {
        Modulo m = new Modulo("avaliacoes", "Avaliações físicas", "Avaliação física", "📏", "/api/avaliacoes", 0xFF0C8599)
                .campo(new Campo("alunoId", "Aluno", Tipo.SELECAO_API).obrigatorio().endpoint("/api/alunos"))
                .campo(new Campo("professorId", "Avaliador", Tipo.SELECAO_API).endpoint("/api/professores"))
                .campo(new Campo("dataAvaliacao", "Data da avaliação", Tipo.DATA).padrao("HOJE"))
                .campo(new Campo("peso", "Peso (kg)", Tipo.DECIMAL).obrigatorio())
                .campo(new Campo("altura", "Altura (m)", Tipo.DECIMAL).obrigatorio().dica("Ex.: 1,75"))
                .campo(new Campo("percentualGordura", "% de gordura", Tipo.DECIMAL))
                .campo(new Campo("cintura", "Cintura (cm)", Tipo.DECIMAL))
                .campo(new Campo("quadril", "Quadril (cm)", Tipo.DECIMAL))
                .campo(new Campo("braco", "Braço (cm)", Tipo.DECIMAL))
                .campo(new Campo("coxa", "Coxa (cm)", Tipo.DECIMAL))
                .campo(new Campo("observacoes", "Observações", Tipo.MULTILINHA));
        m.tituloItem = o -> txt(o, "alunoNome");
        m.subtituloItem = o -> {
            String l1 = data(txt(o, "dataAvaliacao")) + " • " + numero(o.optDouble("peso")) + " kg"
                    + (tem(o, "imc") ? " • IMC " + numero(o.optDouble("imc")) : "");
            String l2 = "";
            if (tem(o, "variacaoPeso")) {
                l2 = "Evolução: " + variacao(o.optDouble("variacaoPeso")) + " kg";
                if (tem(o, "variacaoGordura")) l2 += " • " + variacao(o.optDouble("variacaoGordura")) + "% gordura";
            } else {
                l2 = "Primeira avaliação";
            }
            return l1 + "\n" + l2;
        };
        m.seloItem = o -> {
            String c = txt(o, "classificacaoImc");
            if (c.isEmpty()) return null;
            int cor = c.equals("Peso normal") ? VERDE : c.equals("Sobrepeso") || c.equals("Abaixo do peso") ? LARANJA : VERMELHO;
            return new Modulo.Selo(c.toUpperCase(), cor);
        };
        m.resumo = Modulos::resumoAvaliacao;
        return m;
    }

    private static String resumoAvaliacao(JSONObject o) {
        StringBuilder sb = new StringBuilder();
        if (tem(o, "imc")) {
            sb.append("IMC: ").append(numero(o.optDouble("imc"))).append(" — ").append(txt(o, "classificacaoImc"));
        }
        if (tem(o, "variacaoPeso")) {
            sb.append("\nEvolução desde a avaliação anterior:\n  Peso: ").append(variacao(o.optDouble("variacaoPeso"))).append(" kg");
            if (tem(o, "variacaoGordura")) {
                sb.append("\n  Gordura: ").append(variacao(o.optDouble("variacaoGordura"))).append(" pontos percentuais");
            }
        } else {
            sb.append("\nPrimeira avaliação deste aluno.");
        }
        return sb.toString();
    }
}
