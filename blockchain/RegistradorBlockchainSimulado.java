package blockchain;

import java.util.Map;
import java.util.UUID;

/**
 * Implementação FALSA, usada apenas para conseguir rodar os exemplos deste
 * módulo (classe Main) sem depender do módulo real de Blockchain/Block.
 *
 * SUBSTITUA esta classe, na integração real, por algo como
 * "RegistradorBlockchainReal" que de fato cria um Block e o adiciona à sua
 * Blockchain já existente (veja o exemplo de código no Javadoc de
 * {@link RegistradorBlockchain}).
 */
public class RegistradorBlockchainSimulado implements RegistradorBlockchain {

    @Override
    public String registrarTransacao(String tipoEvento, Map<String, Object> dados) {
        // Aqui, no lugar deste "hash falso", entraria a chamada real a
        // blockchain.adicionarBloco(new Block(...)) e o retorno do hash
        // de verdade calculado pelo seu módulo de Block.
        String hashSimulado = "SIMULADO-" + UUID.randomUUID();
        System.out.println("[BLOCKCHAIN-SIMULADA] Evento: " + tipoEvento
                + " | Dados: " + dados
                + " | Hash: " + hashSimulado);
        return hashSimulado;
    }
}
