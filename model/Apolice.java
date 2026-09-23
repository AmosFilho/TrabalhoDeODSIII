package model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Contrato do seguro. É a peça central consultada pelo SmartContract
 * para decidir se um sinistro pode ser aceito: vigência, coberturas
 * contratadas e valor segurado (limite máximo de indenização).
 */
public class Apolice {

    private final String id;
    private final Cliente cliente;
    private final Veiculo veiculo;
    private final Seguradora seguradora;
    private final Map<TipoCobertura, Boolean> coberturas;
    private final BigDecimal valorSegurado;
    private final LocalDate dataInicio;
    private final LocalDate dataFim;
    private StatusApolice status;
    private final List<Sinistro> sinistros = new ArrayList<>();

    public Apolice(String id, Cliente cliente, Veiculo veiculo, Seguradora seguradora,
                    Map<TipoCobertura, Boolean> coberturas, BigDecimal valorSegurado,
                    LocalDate dataInicio, LocalDate dataFim, StatusApolice status) {
        this.id = id;
        this.cliente = cliente;
        this.veiculo = veiculo;
        this.seguradora = seguradora;
        this.coberturas = new EnumMap<>(coberturas);
        this.valorSegurado = valorSegurado;
        this.dataInicio = dataInicio;
        this.dataFim = dataFim;
        this.status = status;
    }

    public String getId() { return id; }
    public Cliente getCliente() { return cliente; }
    public Veiculo getVeiculo() { return veiculo; }
    public Seguradora getSeguradora() { return seguradora; }
    public BigDecimal getValorSegurado() { return valorSegurado; }
    public LocalDate getDataInicio() { return dataInicio; }
    public LocalDate getDataFim() { return dataFim; }
    public StatusApolice getStatus() { return status; }
    public List<Sinistro> getSinistros() { return Collections.unmodifiableList(sinistros); }

    /** Só a Seguradora (mesmo pacote) pode alterar o status da apólice. */
    void setStatus(StatusApolice status) {
        this.status = status;
    }

    void adicionarSinistro(Sinistro sinistro) {
        sinistros.add(sinistro);
    }

    public boolean possuiCobertura(TipoCobertura tipo) {
        return coberturas.getOrDefault(tipo, false);
    }

    /** Vigente = status ATIVA e a data informada está dentro do período contratado. */
    public boolean estaVigente(LocalDate data) {
        return status == StatusApolice.ATIVA
                && !data.isBefore(dataInicio)
                && !data.isAfter(dataFim);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Apólice #").append(id).append("\n\n");
        sb.append("Cliente: ").append(cliente.getNome()).append("\n");
        sb.append("Veículo: ").append(veiculo.getModelo()).append("\n\n");
        sb.append("Cobertura:\n");
        for (TipoCobertura tipo : TipoCobertura.values()) {
            sb.append(possuiCobertura(tipo) ? "✓ " : "✗ ").append(tipo).append("\n");
        }
        sb.append("\nValor segurado: R$ ").append(valorSegurado).append("\n\n");
        sb.append("Início: ").append(dataInicio).append("\n");
        sb.append("Fim: ").append(dataFim).append("\n\n");
        sb.append("Status: ").append(status);
        return sb.toString();
    }
}
