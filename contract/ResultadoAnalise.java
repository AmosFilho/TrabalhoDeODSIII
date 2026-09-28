package contract;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Resultado da análise de um sinistro feita pelo SmartContract:
 * se foi aprovado ou não, o motivo, e a lista de regras verificadas
 * (útil para exibir ao cliente/seguradora o "porquê" da decisão).
 */
public class ResultadoAnalise {

    private final boolean aprovado;
    private final String motivo;
    private final List<String> regrasVerificadas;
    private final BigDecimal franquia;
    private final BigDecimal valorIndenizacao;

    public ResultadoAnalise(boolean aprovado, String motivo, List<String> regrasVerificadas) {
        this(aprovado, motivo, regrasVerificadas, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    public ResultadoAnalise(boolean aprovado, String motivo, List<String> regrasVerificadas,
                            BigDecimal franquia, BigDecimal valorIndenizacao) {
        this.aprovado = aprovado;
        this.motivo = motivo;
        this.regrasVerificadas = regrasVerificadas == null ? new ArrayList<>() : regrasVerificadas;
        this.franquia = franquia == null ? BigDecimal.ZERO : franquia;
        this.valorIndenizacao = valorIndenizacao == null ? BigDecimal.ZERO : valorIndenizacao;
    }

    public boolean isAprovado() { return aprovado; }
    public String getMotivo() { return motivo; }
    public List<String> getRegrasVerificadas() { return Collections.unmodifiableList(regrasVerificadas); }
    public BigDecimal getFranquia() { return franquia; }
    public BigDecimal getValorIndenizacao() { return valorIndenizacao; }
}
