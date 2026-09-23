package model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Evento comunicado pelo cliente que pode gerar indenização.
 * Ciclo de vida: EM_ANALISE -> APROVADO | REJEITADO -> (se aprovado) PAGO.
 */
public class Sinistro {

    private final String id;
    private final Apolice apolice;
    private final TipoCobertura tipo;
    private final BigDecimal valorSolicitado;
    private final LocalDate data;
    private StatusSinistro status;

    // Preenchido pelo SmartContract: por que foi aprovado ou rejeitado.
    private String motivo;

    /**
     * === PONTO DE INTEGRAÇÃO COM A BLOCKCHAIN ===
     * Campo reservado para guardar o hash do Block em que a decisão
     * (aprovação/rejeição) ou o pagamento deste sinistro ficou registrado
     * na blockchain já existente no projeto. É preenchido pela Seguradora,
     * através do componente RegistradorBlockchain
     * (veja com.segurochain.blockchain.RegistradorBlockchain), logo depois
     * de cada evento relevante. Com esse hash em mãos, qualquer pessoa
     * pode ir até a Blockchain e auditar a decisão de forma independente.
     */
    private String hashBlockchain;

    public Sinistro(String id, Apolice apolice, TipoCobertura tipo, BigDecimal valorSolicitado, LocalDate data) {
        this.id = id;
        this.apolice = apolice;
        this.tipo = tipo;
        this.valorSolicitado = valorSolicitado;
        this.data = data;
        this.status = StatusSinistro.EM_ANALISE;
    }

    public String getId() { return id; }
    public Apolice getApolice() { return apolice; }
    public TipoCobertura getTipo() { return tipo; }
    public BigDecimal getValorSolicitado() { return valorSolicitado; }
    public LocalDate getData() { return data; }
    public StatusSinistro getStatus() { return status; }
    public String getMotivo() { return motivo; }
    public String getHashBlockchain() { return hashBlockchain; }

    /**
     * Propositalmente sem "public": a única forma correta de mudar o
     * status de um sinistro é através da Seguradora chamando o
     * SmartContract (ver Seguradora#analisarSinistro / #registrarPagamento).
     * Isso implementa a regra: "a seguradora não deveria simplesmente
     * alterar o estado de um sinistro sem passar pelas regras definidas
     * pelo Smart Contract".
     */
    void atualizarStatus(StatusSinistro novoStatus, String motivo) {
        this.status = novoStatus;
        this.motivo = motivo;
    }

    void registrarHashBlockchain(String hash) {
        this.hashBlockchain = hash;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Sinistro #").append(id).append("\n\n");
        sb.append("Apólice: #").append(apolice.getId()).append("\n");
        sb.append("Tipo: ").append(tipo).append("\n");
        sb.append("Valor solicitado: R$ ").append(valorSolicitado).append("\n");
        sb.append("Data: ").append(data).append("\n\n");
        sb.append("Status: ").append(status);
        if (motivo != null) {
            sb.append("\nMotivo: ").append(motivo);
        }
        if (hashBlockchain != null) {
            sb.append("\nHash na blockchain: ").append(hashBlockchain);
        }
        return sb.toString();
    }
}
