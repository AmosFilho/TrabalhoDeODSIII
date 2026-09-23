package model;

import blockchain.RegistradorBlockchain;
import contract.ResultadoAnalise;
import contract.SmartContract;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cadastra apólices, analisa sinistros e registra pagamentos.
 *
 * Regra de negócio importante: a Seguradora NUNCA decide sozinha se um
 * sinistro é aprovado ou rejeitado — ela sempre delega essa decisão ao
 * SmartContract (ver {@link #analisarSinistro}). Isso é o que impede a
 * seguradora de simplesmente alterar o estado de um sinistro "por fora"
 * das regras contratuais.
 */
public class Seguradora {

    private final String id;
    private final String nome;
    private final String wallet;
    private final List<Apolice> apolices = new ArrayList<>();
    private final List<Sinistro> sinistros = new ArrayList<>();

    /**
     * === PONTO DE INTEGRAÇÃO COM A BLOCKCHAIN ===
     * Referência para o componente responsável por gravar eventos na
     * blockchain já existente no projeto. Pode ser null (ex.: em testes),
     * caso em que a Seguradora simplesmente deixa de registrar on-chain.
     * Em produção, injete aqui uma implementação real de
     * RegistradorBlockchain (ver com.segurochain.blockchain).
     */
    private final RegistradorBlockchain registradorBlockchain;

    public Seguradora(String id, String nome, String wallet) {
        this(id, nome, wallet, null);
    }

    public Seguradora(String id, String nome, String wallet, RegistradorBlockchain registradorBlockchain) {
        this.id = id;
        this.nome = nome;
        this.wallet = wallet;
        this.registradorBlockchain = registradorBlockchain;
    }

    public String getId() { return id; }
    public String getNome() { return nome; }
    public String getWallet() { return wallet; }
    public List<Apolice> consultarApolices() { return Collections.unmodifiableList(apolices); }
    public List<Sinistro> consultarSinistros() { return Collections.unmodifiableList(sinistros); }

    public Apolice criarApolice(String id, Cliente cliente, Veiculo veiculo,
                                 Map<TipoCobertura, Boolean> coberturas, BigDecimal valorSegurado,
                                 LocalDate dataInicio, LocalDate dataFim) {
        Apolice apolice = new Apolice(id, cliente, veiculo, this, coberturas, valorSegurado,
                dataInicio, dataFim, StatusApolice.ATIVA);
        apolices.add(apolice);
        cliente.vincularApolice(apolice);
        return apolice;
    }

    public void ativarApolice(Apolice apolice) {
        apolice.setStatus(StatusApolice.ATIVA);
    }

    public void cancelarApolice(Apolice apolice) {
        apolice.setStatus(StatusApolice.CANCELADA);
    }

    /** Cria o registro do sinistro (status inicial EM_ANALISE). Ainda não passou pelo SmartContract. */
    public Sinistro registrarSinistro(Apolice apolice, TipoCobertura tipo, BigDecimal valorSolicitado, LocalDate data) {
        String sinistroId = String.valueOf(sinistros.size() + 1);
        Sinistro sinistro = new Sinistro(sinistroId, apolice, tipo, valorSolicitado, data);
        apolice.adicionarSinistro(sinistro);
        sinistros.add(sinistro);
        return sinistro;
    }

    /**
     * Único caminho permitido para decidir um sinistro. A Seguradora repassa
     * apólice + sinistro ao SmartContract, que aplica as regras (vigência,
     * cobertura, limite de valor) e devolve um resultado; a Seguradora
     * apenas aplica esse resultado ao Sinistro.
     *
     * === INTEGRAÇÃO COM A BLOCKCHAIN ===
     * A decisão do SmartContract é exatamente o tipo de evento que deve
     * virar um registro imutável. É aqui, logo após obter o
     * ResultadoAnalise, que entra a integração:
     *
     *   1) montamos um Map com os dados relevantes da decisão
     *      (idSinistro, idApolice, tipo, valorSolicitado, aprovado, motivo);
     *   2) chamamos registradorBlockchain.registrarTransacao("ANALISE_SINISTRO", dados);
     *   3) a implementação real desse método cria um novo Block com esses
     *      dados, chama blockchain.adicionarBloco(novoBloco) na Blockchain
     *      já existente, e devolve o hash gerado;
     *   4) guardamos esse hash no próprio Sinistro
     *      (sinistro.registrarHashBlockchain(hash)) para auditoria futura.
     */
    public ResultadoAnalise analisarSinistro(Sinistro sinistro) {
        ResultadoAnalise resultado = SmartContract.analisarSinistro(sinistro.getApolice(), sinistro);

        StatusSinistro novoStatus = resultado.isAprovado() ? StatusSinistro.APROVADO : StatusSinistro.REJEITADO;
        sinistro.atualizarStatus(novoStatus, resultado.getMotivo());

        if (registradorBlockchain != null) {
            Map<String, Object> dados = new HashMap<>();
            dados.put("idSinistro", sinistro.getId());
            dados.put("idApolice", sinistro.getApolice().getId());
            dados.put("tipo", sinistro.getTipo().name());
            dados.put("valorSolicitado", sinistro.getValorSolicitado());
            dados.put("aprovado", resultado.isAprovado());
            dados.put("motivo", resultado.getMotivo());

            String hash = registradorBlockchain.registrarTransacao("ANALISE_SINISTRO", dados);
            sinistro.registrarHashBlockchain(hash);
        }

        return resultado;
    }

    /**
     * Registra o pagamento de um sinistro já APROVADO.
     *
     * === INTEGRAÇÃO COM A BLOCKCHAIN ===
     * Este é o evento mais crítico do fluxo: dinheiro efetivamente mudando
     * de mãos. Numa arquitetura mais avançada, esse pagamento poderia até
     * ser executado diretamente por um contrato on-chain (ex.: transferindo
     * um token/criptoativo da wallet da seguradora para a wallet do
     * cliente). Aqui, a chamada a registradorBlockchain.registrarTransacao
     * é o gancho para (a) dar transparência e (b) permitir auditar que o
     * pagamento realmente ocorreu — criando um novo Block com os dados
     * financeiros da transação.
     */
    public void registrarPagamento(Sinistro sinistro) {
        if (sinistro.getStatus() != StatusSinistro.APROVADO) {
            throw new IllegalStateException("Somente sinistros APROVADOS podem ser pagos.");
        }

        sinistro.atualizarStatus(StatusSinistro.PAGO, sinistro.getMotivo());

        if (registradorBlockchain != null) {
            Map<String, Object> dados = new HashMap<>();
            dados.put("idSinistro", sinistro.getId());
            dados.put("idApolice", sinistro.getApolice().getId());
            dados.put("valorPago", sinistro.getValorSolicitado());
            dados.put("walletOrigem", this.wallet);
            dados.put("walletDestino", sinistro.getApolice().getCliente().getWallet());

            String hash = registradorBlockchain.registrarTransacao("PAGAMENTO_SINISTRO", dados);
            sinistro.registrarHashBlockchain(hash);
        }
    }

    @Override
    public String toString() {
        return "Seguradora{id='" + id + "', nome='" + nome + "'}";
    }
}
