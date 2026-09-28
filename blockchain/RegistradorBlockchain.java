package blockchain;

import java.util.Map;

/**
 * ============================================================================
 *  PONTO DE INTEGRAÇÃO COM O MÓDULO DE BLOCKCHAIN/BLOCK JÁ EXISTENTE
 * ============================================================================
 *
 * Esta interface é a "tomada" que este módulo de seguros usa para falar
 * com a blockchain, sem precisar conhecer os detalhes de como um Block é
 * criado, minerado ou encadeado. Como pedido, a implementação de Block e
 * Blockchain NÃO está neste módulo — ela já existe no seu projeto. Basta
 * criar uma classe que implemente esta interface delegando para as suas
 * classes reais. Algo como:
 *
 * <pre>
 *   public class RegistradorBlockchainReal implements RegistradorBlockchain {
 *
 *       private final Blockchain blockchain; // sua classe já existente
 *
 *       public RegistradorBlockchainReal(Blockchain blockchain) {
 *           this.blockchain = blockchain;
 *       }
 *
 *       {@literal @}Override
 *       public String registrarTransacao(String tipoEvento, Map&lt;String, Object&gt; dados) {
 *           // 1) Serializa os dados do evento (ex.: para JSON ou String simples)
 *           String payload = tipoEvento + ":" + dados;
 *
 *           // 2) Cria um novo Block encadeado ao último bloco da cadeia
 *           Block ultimoBloco = blockchain.getUltimoBloco();
 *           Block novoBloco = new Block(ultimoBloco.getHash(), payload);
 *
 *           // 3) Adiciona (e, dependendo da sua implementação, minera) o bloco
 *           blockchain.adicionarBloco(novoBloco);
 *
 *           // 4) Devolve o hash para quem chamou (Seguradora), que guarda
 *           //    essa referência no próprio Sinistro para consulta futura.
 *           return novoBloco.getHash();
 *       }
 *   }
 * </pre>
 *
 * Quem consome esta interface é a classe {@code Seguradora}, exatamente
 * nos dois momentos em que a integridade/imutabilidade da blockchain
 * agrega valor real ao negócio:
 *
 *   1) Seguradora#analisarSinistro  -> grava a DECISÃO do SmartContract
 *      (aprovado/rejeitado e o motivo), tornando-a auditável e à prova
 *      de alteração posterior por qualquer uma das partes.
 *
 *   2) Seguradora#registrarPagamento -> grava o PAGAMENTO da indenização,
 *      criando um comprovante imutável de que o valor foi de fato pago.
 *
 * Em uma versão mais avançada, você poderia até inverter a dependência:
 * em vez do SmartContract Java apenas simular as regras e "avisar" a
 * blockchain depois, as regras de aprovação (vigência, cobertura, limite
 * de valor) poderiam ser reimplementadas dentro de um contrato on-chain
 * de verdade (ex.: Solidity), e esta classe SmartContract em Java passaria
 * a ser só um cliente que invoca esse contrato remoto.
 */
public interface RegistradorBlockchain {

    /**
     * Registra um evento de negócio de forma imutável.
     *
     * @param tipoEvento identifica o tipo do evento (ex.: "ANALISE_SINISTRO",
     *                    "PAGAMENTO_SINISTRO"), útil para quem for ler a
     *                    blockchain depois e precisar filtrar/entender os blocos.
     * @param dados       payload com os dados relevantes do evento (ids,
     *                    valores, datas, resultado da análise etc.).
     * @return o hash do bloco recém-criado, para ser guardado pela entidade
     *         de negócio correspondente (ex.: Sinistro#registrarHashBlockchain).
     */
    String registrarTransacao(String tipoEvento, Map<String, Object> dados);
}
