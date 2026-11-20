package com.academia.app;

import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Tela inicial: resumo (dashboard) e acesso aos 6 módulos. */
public class MainActivity extends AppCompatActivity {

    private TextView txtConexao;
    private LinearLayout painelResumo;
    private SwipeRefreshLayout swipe;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        MenuItem config = toolbar.getMenu().add("Servidor");
        config.setIcon(android.R.drawable.ic_menu_manage);
        config.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        if (config.getIcon() != null) config.getIcon().setTint(0xFFFFFFFF);
        toolbar.setOnMenuItemClickListener(item -> {
            abrirConfiguracoes();
            return true;
        });

        txtConexao = findViewById(R.id.txtConexao);
        painelResumo = findViewById(R.id.painelResumo);
        swipe = findViewById(R.id.swipe);
        swipe.setColorSchemeColors(getColor(R.color.primaria));
        swipe.setOnRefreshListener(this::carregarResumo);
        txtConexao.setOnClickListener(v -> abrirConfiguracoes());

        montarModulos();
    }

    @Override
    protected void onResume() {
        super.onResume();
        carregarResumo();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void definirConexao(boolean ok, String texto) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(10));
        bg.setColor(ok ? 0xFF2F9E44 : 0xFFE03131);
        txtConexao.setBackground(bg);
        txtConexao.setText(texto);
    }

    private void carregarResumo() {
        swipe.setRefreshing(true);
        Api.get(this, "/api/dashboard", new Api.Resposta() {
            @Override
            public void sucesso(String corpo) {
                swipe.setRefreshing(false);
                try {
                    mostrarResumo(new JSONObject(corpo));
                    definirConexao(true, "● Conectado a " + Api.servidor(MainActivity.this));
                } catch (Exception e) {
                    definirConexao(false, "Resposta inválida do servidor");
                }
            }

            @Override
            public void erro(String mensagem) {
                swipe.setRefreshing(false);
                definirConexao(false, mensagem + "\n\nToque aqui para configurar o servidor.");
                painelResumo.removeAllViews();
            }
        });
    }

    private void mostrarResumo(JSONObject d) {
        painelResumo.removeAllViews();
        List<View> cards = new ArrayList<>();
        cards.add(cardNumero(String.valueOf(d.optLong("alunosAtivos")), "Alunos ativos\n(" + d.optLong("totalAlunos") + " no total)",
                Modulos.AZUL, v -> abrirLista("alunos", null)));
        cards.add(cardNumero(String.valueOf(d.optLong("professores")), "Professores",
                0xFFE8590C, v -> abrirLista("professores", null)));
        cards.add(cardNumero(String.valueOf(d.optLong("mensalidadesAtrasadas")), "Mensalidades\natrasadas",
                Modulos.VERMELHO, v -> abrirLista("mensalidades", "ATRASADA")));
        cards.add(cardNumero(String.valueOf(d.optLong("mensalidadesPendentes")), "Mensalidades\npendentes",
                Modulos.LARANJA, v -> abrirLista("mensalidades", "PENDENTE")));
        cards.add(cardNumero(Formatos.dinheiro(d.optDouble("recebidoNoMes")), "Recebido\neste mês",
                Modulos.VERDE, v -> abrirLista("mensalidades", "PAGA")));
        cards.add(cardNumero(Formatos.dinheiro(d.optDouble("valorEmAberto")), "Valor\nem aberto",
                Modulos.CINZA, v -> abrirLista("mensalidades", null)));
        adicionarEmGrade(painelResumo, cards);
    }

    private void montarModulos() {
        LinearLayout painel = findViewById(R.id.painelModulos);
        List<View> cards = new ArrayList<>();
        for (Modulo m : Modulos.todos()) {
            cards.add(cardModulo(m));
        }
        adicionarEmGrade(painel, cards);
    }

    /** Distribui os cards em linhas de 2 colunas. */
    private void adicionarEmGrade(LinearLayout painel, List<View> cards) {
        for (int i = 0; i < cards.size(); i += 2) {
            LinearLayout linha = new LinearLayout(this);
            linha.setOrientation(LinearLayout.HORIZONTAL);
            for (int j = i; j < i + 2; j++) {
                FrameLayout celula = new FrameLayout(this);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1);
                celula.setLayoutParams(lp);
                celula.setPadding(dp(4), dp(4), dp(4), dp(4));
                if (j < cards.size()) celula.addView(cards.get(j));
                linha.addView(celula);
            }
            painel.addView(linha);
        }
    }

    private MaterialCardView novoCard(View.OnClickListener clique) {
        MaterialCardView card = new MaterialCardView(this);
        card.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        card.setRadius(dp(14));
        card.setCardElevation(dp(1));
        card.setStrokeWidth(0);
        card.setClickable(true);
        card.setOnClickListener(clique);
        return card;
    }

    private View cardNumero(String valor, String rotulo, int cor, View.OnClickListener clique) {
        MaterialCardView card = novoCard(clique);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(12), dp(14), dp(12));

        TextView v = new TextView(this);
        v.setText(valor);
        v.setTextSize(22);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setTextColor(cor);
        TextView r = new TextView(this);
        r.setText(rotulo);
        r.setTextSize(12);
        r.setTextColor(getColor(R.color.texto_secundario));

        box.addView(v);
        box.addView(r);
        card.addView(box);
        return card;
    }

    private View cardModulo(Modulo m) {
        MaterialCardView card = novoCard(v -> abrirLista(m.id, null));
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(12), dp(18), dp(12), dp(18));

        TextView icone = new TextView(this);
        icone.setText(m.icone);
        icone.setTextSize(26);
        icone.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor((m.cor & 0x00FFFFFF) | 0x22000000);
        icone.setBackground(bg);
        icone.setLayoutParams(new LinearLayout.LayoutParams(dp(60), dp(60)));

        TextView titulo = new TextView(this);
        titulo.setText(m.titulo);
        titulo.setTextSize(15);
        titulo.setTypeface(Typeface.DEFAULT_BOLD);
        titulo.setTextColor(getColor(R.color.texto));
        titulo.setGravity(Gravity.CENTER);
        titulo.setPadding(0, dp(10), 0, 0);

        box.addView(icone);
        box.addView(titulo);
        card.addView(box);
        return card;
    }

    private void abrirLista(String modulo, String status) {
        Intent i = new Intent(this, ListaActivity.class);
        i.putExtra(ListaActivity.EXTRA_MODULO, modulo);
        if (status != null) i.putExtra(ListaActivity.EXTRA_STATUS, status);
        startActivity(i);
    }

    private void abrirConfiguracoes() {
        TextInputLayout layout = new TextInputLayout(this, null,
                com.google.android.material.R.attr.textInputOutlinedStyle);
        layout.setHint("Endereço do servidor");
        layout.setHelperText("Emulador: http://10.0.2.2:8080\nCelular: http://IP-DO-PC:8080 (mesmo Wi-Fi)");
        TextInputEditText edt = new TextInputEditText(layout.getContext());
        edt.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        edt.setText(Api.servidor(this));
        layout.addView(edt);
        FrameLayout wrap = new FrameLayout(this);
        wrap.setPadding(dp(20), dp(8), dp(20), 0);
        wrap.addView(layout);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Servidor (back-end)")
                .setView(wrap)
                .setPositiveButton("Salvar", (d, w) -> {
                    String url = edt.getText() == null ? "" : edt.getText().toString();
                    if (!url.trim().isEmpty()) Api.salvarServidor(this, url);
                    carregarResumo();
                })
                .setNeutralButton("Padrão", (d, w) -> {
                    Api.salvarServidor(this, Api.SERVIDOR_PADRAO);
                    carregarResumo();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }
}
