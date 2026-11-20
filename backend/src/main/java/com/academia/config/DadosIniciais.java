package com.academia.config;

import com.academia.model.*;
import com.academia.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.persistence.EntityManager;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Roda quando o sistema sobe:
 *  1. ajusta bancos criados antes do login (CPF único por academia, não no sistema todo);
 *  2. garante que existe a academia padrão, com um administrador e um funcionário de exemplo;
 *  3. registros antigos sem academia passam a ser da academia padrão;
 *  4. banco vazio → cadastra os dados de exemplo nessa academia.
 */
@Component
public class DadosIniciais implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DadosIniciais.class);

    /** Logins de exemplo da academia padrão (também estão no README). Troque a senha em produção. */
    static final String EMAIL_ADMIN = "admin@forcatotal.com";
    static final String EMAIL_FUNCIONARIO = "recepcao@forcatotal.com";
    static final String SENHA_EXEMPLO = "forca2026";

    private static final List<String> TABELAS_DA_ACADEMIA = List.of(
            "planos", "professores", "alunos", "mensalidades", "fichas_treino", "avaliacoes_fisicas");

    private final PlanoRepository planos;
    private final ProfessorRepository professores;
    private final AlunoRepository alunos;
    private final MensalidadeRepository mensalidades;
    private final FichaTreinoRepository fichas;
    private final AvaliacaoFisicaRepository avaliacoes;
    private final AcademiaRepository academias;
    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;
    private final EntityManager entityManager;

    public DadosIniciais(PlanoRepository planos, ProfessorRepository professores, AlunoRepository alunos,
                         MensalidadeRepository mensalidades, FichaTreinoRepository fichas,
                         AvaliacaoFisicaRepository avaliacoes, AcademiaRepository academias,
                         UsuarioRepository usuarios, PasswordEncoder passwordEncoder, JdbcTemplate jdbc,
                         EntityManager entityManager) {
        this.planos = planos;
        this.professores = professores;
        this.alunos = alunos;
        this.mensalidades = mensalidades;
        this.fichas = fichas;
        this.avaliacoes = avaliacoes;
        this.academias = academias;
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
        this.jdbc = jdbc;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public void run(String... args) {
        removerCpfUnicoAntigo();
        liberarPerfilDono();
        boolean bancoVazio = planos.count() == 0 && alunos.count() == 0 && professores.count() == 0;
        Academia padrao = academias.findFirstByOrderByIdAsc().orElseGet(this::criarAcademiaPadrao);
        if (bancoVazio) cadastrarExemplos();
        entityManager.flush();
        adotarRegistrosSemAcademia(padrao);
    }

    private Academia criarAcademiaPadrao() {
        Academia a = academias.save(new Academia("Academia Força Total", "São Paulo"));
        usuarios.save(usuario(a, "Marcos Andrade", EMAIL_ADMIN, Perfil.ADMIN));
        usuarios.save(usuario(a, "Paula Ribeiro", EMAIL_FUNCIONARIO, Perfil.FUNCIONARIO));
        log.info("\n\nAcademia padrão criada. Logins de exemplo (senha {}):\n  admin:       {}\n  funcionário: {}\n",
                SENHA_EXEMPLO, EMAIL_ADMIN, EMAIL_FUNCIONARIO);
        return a;
    }

    private Usuario usuario(Academia a, String nome, String email, Perfil perfil) {
        Usuario u = new Usuario();
        u.setAcademia(a);
        u.setNome(nome);
        u.setEmail(email);
        u.setPerfil(perfil);
        u.setSenhaHash(passwordEncoder.encode(SENHA_EXEMPLO));
        return u;
    }

    /** Registros de antes do sistema de login (academia_id vazio) passam a ser da academia padrão. */
    private void adotarRegistrosSemAcademia(Academia padrao) {
        int total = 0;
        for (String tabela : TABELAS_DA_ACADEMIA) {
            total += jdbc.update("UPDATE " + tabela + " SET academia_id = ? WHERE academia_id IS NULL", padrao.getId());
        }
        if (total > 0) log.info("{} registro(s) sem academia foram vinculados à academia \"{}\".", total, padrao.getNome());
    }

    /**
     * Bancos criados antes do login tinham o CPF único no sistema inteiro. Agora o CPF é único
     * dentro de cada academia (a mesma pessoa pode treinar em duas academias), então a regra antiga sai.
     */
    private void removerCpfUnicoAntigo() {
        String sql = """
                SELECT tc.TABLE_NAME, tc.CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc
                WHERE tc.CONSTRAINT_TYPE = 'UNIQUE' AND tc.TABLE_NAME IN ('ALUNOS', 'PROFESSORES')
                  AND (SELECT COUNT(*) FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE k
                       WHERE k.CONSTRAINT_NAME = tc.CONSTRAINT_NAME AND k.TABLE_NAME = tc.TABLE_NAME) = 1
                  AND EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE k
                       WHERE k.CONSTRAINT_NAME = tc.CONSTRAINT_NAME AND k.TABLE_NAME = tc.TABLE_NAME
                         AND k.COLUMN_NAME = 'CPF')
                """;
        try {
            for (Map<String, Object> linha : jdbc.queryForList(sql)) {
                jdbc.execute("ALTER TABLE " + linha.get("TABLE_NAME") + " DROP CONSTRAINT " + linha.get("CONSTRAINT_NAME"));
                log.info("Regra antiga de CPF único removida da tabela {}.", linha.get("TABLE_NAME"));
            }
        } catch (Exception e) {
            log.warn("Não foi possível revisar as regras de CPF único: {}", e.getMessage());
        }
    }

    /**
     * Bancos criados antes do plano Rede não aceitam o perfil DONO na coluna usuarios.perfil:
     *  - no H2, o Hibernate cria a coluna como tipo ENUM('ADMIN','FUNCIONARIO'): o tipo ganha 'DONO' no fim
     *    (no fim para não mudar a ordem dos valores que já existem);
     *  - em outros casos, pode haver uma regra CHECK listando os perfis da época: essa regra sai
     *    (o próprio Java já só grava valores válidos do enum Perfil).
     */
    private void liberarPerfilDono() {
        try {
            List<String> valores = jdbc.queryForList("""
                    SELECT e.VALUE_NAME FROM INFORMATION_SCHEMA.ENUM_VALUES e
                    JOIN INFORMATION_SCHEMA.COLUMNS c ON c.TABLE_NAME = e.OBJECT_NAME AND c.DTD_IDENTIFIER = e.ENUM_IDENTIFIER
                    WHERE e.OBJECT_NAME = 'USUARIOS' AND c.COLUMN_NAME = 'PERFIL' ORDER BY e.VALUE_ORDINAL
                    """, String.class);
            if (!valores.isEmpty() && !valores.contains(Perfil.DONO.name())) {
                List<String> novos = new java.util.ArrayList<>(valores);
                novos.add(Perfil.DONO.name());
                String tipo = novos.stream().map(v -> "'" + v + "'").collect(java.util.stream.Collectors.joining(", ", "ENUM(", ")"));
                jdbc.execute("ALTER TABLE USUARIOS ALTER COLUMN PERFIL SET DATA TYPE " + tipo);
                log.info("Coluna usuarios.perfil agora aceita o dono da rede: {}", tipo);
            }
        } catch (Exception e) {
            log.warn("Não foi possível revisar o tipo da coluna de perfis: {}", e.getMessage());
        }
        String sql = """
                SELECT tc.CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc
                JOIN INFORMATION_SCHEMA.CHECK_CONSTRAINTS cc ON cc.CONSTRAINT_NAME = tc.CONSTRAINT_NAME
                WHERE tc.TABLE_NAME = 'USUARIOS' AND tc.CONSTRAINT_TYPE = 'CHECK'
                  AND UPPER(cc.CHECK_CLAUSE) LIKE '%PERFIL%' AND UPPER(cc.CHECK_CLAUSE) NOT LIKE '%DONO%'
                """;
        try {
            for (Map<String, Object> linha : jdbc.queryForList(sql)) {
                jdbc.execute("ALTER TABLE USUARIOS DROP CONSTRAINT " + linha.get("CONSTRAINT_NAME"));
                log.info("Regra antiga de perfis removida da tabela USUARIOS (agora aceita o dono da rede).");
            }
        } catch (Exception e) {
            log.warn("Não foi possível revisar a regra de perfis: {}", e.getMessage());
        }
    }

    private void cadastrarExemplos() {
        log.info("Banco vazio: cadastrando dados de exemplo...");
        LocalDate hoje = LocalDate.now();

        Plano mensal = planos.save(plano("Mensal", "Acesso livre à musculação", "119.90", 1));
        Plano trimestral = planos.save(plano("Trimestral", "Musculação + aulas coletivas", "99.90", 3));
        Plano anual = planos.save(plano("Anual", "Acesso total + avaliação física mensal", "79.90", 12));

        Professor carlos = professores.save(professor("Carlos Mendes", "111.222.333-44", "carlos@academia.com",
                "(11) 98888-1111", "Musculação", "012345-G/SP"));
        Professor juliana = professores.save(professor("Juliana Rocha", "555.666.777-88", "juliana@academia.com",
                "(11) 97777-2222", "Funcional e Pilates", "067890-G/SP"));

        Aluno ana = alunos.save(aluno("Ana Souza", "123.456.789-00", "ana@email.com", "(11) 91234-5678",
                LocalDate.of(1998, 4, 12), hoje.minusMonths(3), anual));
        Aluno bruno = alunos.save(aluno("Bruno Lima", "987.654.321-00", "bruno@email.com", "(11) 92345-6789",
                LocalDate.of(2001, 9, 3), hoje.minusMonths(2), mensal));
        Aluno camila = alunos.save(aluno("Camila Torres", "456.789.123-00", "camila@email.com", "(11) 93456-7890",
                LocalDate.of(1995, 1, 25), hoje.minusDays(10), trimestral));

        // Mensalidades: pagas, atrasada e pendentes
        for (int i = 3; i >= 0; i--) {
            LocalDate venc = ana.getDataMatricula().plusMonths(3 - i);
            mensalidades.save(mensalidade(ana, venc, i > 0 ? venc : null, i > 0 ? "PIX" : null));
        }
        mensalidades.save(mensalidade(bruno, bruno.getDataMatricula(), bruno.getDataMatricula(), "Cartão de crédito"));
        mensalidades.save(mensalidade(bruno, bruno.getDataMatricula().plusMonths(1), null, null)); // atrasada
        mensalidades.save(mensalidade(bruno, bruno.getDataMatricula().plusMonths(2), null, null));
        mensalidades.save(mensalidade(camila, camila.getDataMatricula(), camila.getDataMatricula(), "Dinheiro"));
        mensalidades.save(mensalidade(camila, camila.getDataMatricula().plusMonths(1), null, null));

        // Fichas de treino
        FichaTreino a = new FichaTreino();
        a.setAluno(ana);
        a.setProfessor(carlos);
        a.setNome("Treino A - Peito e Tríceps");
        a.setObjetivo("Hipertrofia");
        a.setDataInicio(hoje.minusDays(20));
        a.setDataFim(hoje.plusDays(40));
        a.setObservacoes("Descanso de 60s entre as séries");
        a.setExercicios(new java.util.ArrayList<>(List.of(
                new Exercicio("Supino reto", 4, "10", "40 kg"),
                new Exercicio("Supino inclinado com halteres", 3, "12", "14 kg"),
                new Exercicio("Crucifixo na máquina", 3, "12", "35 kg"),
                new Exercicio("Tríceps na polia", 4, "12", "25 kg"),
                new Exercicio("Tríceps francês", 3, "10", "10 kg"))));
        fichas.save(a);

        FichaTreino b = new FichaTreino();
        b.setAluno(bruno);
        b.setProfessor(juliana);
        b.setNome("Funcional - Condicionamento");
        b.setObjetivo("Emagrecimento");
        b.setDataInicio(hoje.minusDays(7));
        b.setExercicios(new java.util.ArrayList<>(List.of(
                new Exercicio("Agachamento livre", 4, "15", "Peso corporal"),
                new Exercicio("Burpee", 3, "12", null),
                new Exercicio("Prancha", 3, "45 s", null),
                new Exercicio("Remada com elástico", 3, "15", "Elástico médio"))));
        fichas.save(b);

        // Avaliações físicas (Ana tem duas, para mostrar a evolução)
        avaliacoes.save(avaliacao(ana, carlos, hoje.minusMonths(3), 68.5, 1.65, 28.0, 78.0, 100.0, 29.0, 57.0));
        avaliacoes.save(avaliacao(ana, carlos, hoje.minusDays(5), 65.2, 1.65, 25.4, 74.0, 98.0, 29.5, 56.0));
        avaliacoes.save(avaliacao(bruno, juliana, hoje.minusMonths(1), 92.0, 1.80, 24.0, 98.0, 104.0, 36.0, 60.0));

        log.info("Dados de exemplo cadastrados.");
    }

    private static Plano plano(String nome, String descricao, String valor, int meses) {
        Plano p = new Plano();
        p.setNome(nome);
        p.setDescricao(descricao);
        p.setValorMensal(new BigDecimal(valor));
        p.setDuracaoMeses(meses);
        p.setAtivo(true);
        return p;
    }

    private static Professor professor(String nome, String cpf, String email, String tel, String esp, String cref) {
        Professor p = new Professor();
        p.setNome(nome);
        p.setCpf(cpf);
        p.setEmail(email);
        p.setTelefone(tel);
        p.setEspecialidade(esp);
        p.setCref(cref);
        return p;
    }

    private static Aluno aluno(String nome, String cpf, String email, String tel, LocalDate nasc,
                               LocalDate matricula, Plano plano) {
        Aluno a = new Aluno();
        a.setNome(nome);
        a.setCpf(cpf);
        a.setEmail(email);
        a.setTelefone(tel);
        a.setDataNascimento(nasc);
        a.setDataMatricula(matricula);
        a.setPlano(plano);
        a.setAtivo(true);
        return a;
    }

    private static Mensalidade mensalidade(Aluno aluno, LocalDate venc, LocalDate pagamento, String forma) {
        Mensalidade m = new Mensalidade();
        m.setAluno(aluno);
        m.setValor(aluno.getPlano().getValorMensal());
        m.setDataVencimento(venc);
        m.setReferencia(venc.format(java.time.format.DateTimeFormatter.ofPattern("MM/yyyy")));
        m.setDataPagamento(pagamento);
        m.setFormaPagamento(forma);
        return m;
    }

    private static AvaliacaoFisica avaliacao(Aluno aluno, Professor prof, LocalDate data, double peso, double altura,
                                             double gordura, double cintura, double quadril, double braco,
                                             double coxa) {
        AvaliacaoFisica a = new AvaliacaoFisica();
        a.setAluno(aluno);
        a.setProfessor(prof);
        a.setDataAvaliacao(data);
        a.setPeso(peso);
        a.setAltura(altura);
        a.setPercentualGordura(gordura);
        a.setCintura(cintura);
        a.setQuadril(quadril);
        a.setBraco(braco);
        a.setCoxa(coxa);
        return a;
    }
}
