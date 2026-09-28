package model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pessoa que contrata o seguro e registra sinistros.
 */
public class Cliente {

    private final String id;
    private final String nome;

    // Endereço/identidade do cliente. É este valor que, numa integração
    // real com a blockchain, identificaria a "carteira" para onde uma
    // eventual indenização seria transferida.
    private final String wallet;

    private final List<Apolice> apolices = new ArrayList<>();

    public Cliente(String id, String nome, String wallet) {
        this.id = id;
        this.nome = nome;
        this.wallet = wallet;
    }

    public String getId() { return id; }
    public String getNome() { return nome; }
    public String getWallet() { return wallet; }

    /** Chamado pela Seguradora ao emitir uma nova apólice para este cliente. */
    void vincularApolice(Apolice apolice) {
        apolices.add(apolice);
    }

    public List<Apolice> consultarApolices() {
        return Collections.unmodifiableList(apolices);
    }

    public Veiculo consultarVeiculo(Apolice apolice) {
        garantirQueApoliceEDoCliente(apolice);
        return apolice.getVeiculo();
    }

    /**
     * O cliente comunica a abertura de um sinistro. Quem efetivamente cria
     * e guarda o objeto Sinistro é a Seguradora responsável pela apólice
     * (ver {@link Seguradora#registrarSinistro}) — o cliente apenas
     * solicita, e neste momento o sinistro ainda não passou pelo
     * SmartContract (fica em EM_ANALISE).
     */
    public Sinistro abrirSinistro(Apolice apolice, TipoCobertura tipo, BigDecimal valorSolicitado, LocalDate data) {
        garantirQueApoliceEDoCliente(apolice);
        return apolice.getSeguradora().registrarSinistro(apolice, tipo, valorSolicitado, data);
    }

    public StatusSinistro acompanharStatusSinistro(Sinistro sinistro) {
        return sinistro.getStatus();
    }

    /** Histórico de todos os sinistros abertos pelo cliente, em todas as suas apólices. */
    public List<Sinistro> consultarHistorico() {
        List<Sinistro> historico = new ArrayList<>();
        for (Apolice apolice : apolices) {
            historico.addAll(apolice.getSinistros());
        }
        return historico;
    }

    private void garantirQueApoliceEDoCliente(Apolice apolice) {
        if (!apolices.contains(apolice)) {
            throw new IllegalArgumentException("Esta apólice não pertence a este cliente.");
        }
    }

    @Override
    public String toString() {
        return "Cliente{id='" + id + "', nome='" + nome + "'}";
    }
}
