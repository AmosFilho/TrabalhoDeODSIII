package contract;

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

    public ResultadoAnalise(boolean aprovado, String motivo, List<String> regrasVerificadas) {
        this.aprovado = aprovado;
        this.motivo = motivo;
        this.regrasVerificadas = regrasVerificadas == null ? new ArrayList<>() : regrasVerificadas;
    }

    public boolean isAprovado() { return aprovado; }
    public String getMotivo() { return motivo; }
    public List<String> getRegrasVerificadas() { return Collections.unmodifiableList(regrasVerificadas); }
}
