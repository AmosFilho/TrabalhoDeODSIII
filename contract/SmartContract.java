package contract;

import model.Apolice;
import model.Sinistro;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * ============================================================================
 *  SMART CONTRACT (versão Java, "off-chain")
 * ============================================================================
 *
 * Esta classe implementa, em Java puro, exatamente o fluxo de regras
 * descrito no enunciado:
 *
 *   A apólice existe?
 *        -> Está vigente?
 *             -> A cobertura contempla o sinistro?
 *                  -> O valor solicitado está dentro do limite?
 *                       SIM -> permite aprovação
 *                       NÃO -> rejeita
 *
 * IMPORTANTE — relação com a blockchain já existente no seu projeto:
 * esta classe NÃO grava nada na blockchain. Ela só decide (aprova ou
 * rejeita) com base nos dados da Apolice e do Sinistro que já estão em
 * memória. Quem efetivamente leva essa decisão para a blockchain é a
 * classe Seguradora, que:
 *
 *   1) chama SmartContract.analisarSinistro(apolice, sinistro) aqui deste
 *      pacote;
 *   2) pega o ResultadoAnalise devolvido;
 *   3) usa o RegistradorBlockchain (com.segurochain.blockchain) para
 *      gravar essa decisão como um novo Block, de forma imutável.
 *
 * Essa separação é proposital: as REGRAS de negócio (este arquivo) ficam
 * isoladas de COMO elas são tornadas imutáveis/auditáveis (blockchain).
 * Isso também deixa claro qual seria o caminho para, no futuro, mover essas
 * mesmas regras para dentro de um contrato on-chain de verdade (ex.: um
 * contrato Solidity rodando em uma rede Ethereum-compatível, ou "chaincode"
 * em Hyperledger Fabric): a assinatura do método analisarSinistro poderia
 * continuar praticamente a mesma, só a implementação passaria a delegar
 * para uma chamada de contrato remoto em vez de rodar em memória.
 */
public final class SmartContract {

    private SmartContract() {
        // classe utilitária, apenas com métodos estáticos
    }

    public static ResultadoAnalise analisarSinistro(Apolice apolice, Sinistro sinistro) {
        List<String> regras = new ArrayList<>();

        // 1) A apólice existe?
        if (apolice == null) {
            regras.add("✗ Apólice existe");
            return new ResultadoAnalise(false, "Apólice inexistente.", regras);
        }
        regras.add("✓ Apólice existe");

        // 2) Está vigente na data do sinistro?
        if (!apolice.estaVigente(sinistro.getData())) {
            regras.add("✗ Apólice está vigente na data do sinistro");
            return new ResultadoAnalise(
                    false,
                    "Apólice não está ativa/vigente na data do sinistro.",
                    regras);
        }
        regras.add("✓ Apólice está vigente na data do sinistro");

        // 3) A cobertura contempla o tipo de sinistro?
        if (!apolice.possuiCobertura(sinistro.getTipo())) {
            regras.add("✗ Cobertura contempla o tipo de sinistro");
            return new ResultadoAnalise(
                    false,
                    "A cobertura contratada não contempla " + sinistro.getTipo() + ".",
                    regras);
        }
        regras.add("✓ Cobertura contempla o tipo de sinistro");

        // 4) O valor solicitado está dentro do limite (valor segurado)?
        BigDecimal valorSolicitado = sinistro.getValorSolicitado();
        BigDecimal limite = apolice.getValorSegurado();
        if (valorSolicitado.compareTo(limite) > 0) {
            regras.add("✗ Valor solicitado dentro do limite segurado");
            return new ResultadoAnalise(
                    false,
                    "Valor solicitado (R$ " + valorSolicitado + ") excede o valor segurado (R$ " + limite + ").",
                    regras);
        }
        regras.add("✓ Valor solicitado dentro do limite segurado");

        // Todas as regras passaram -> aprova
        return new ResultadoAnalise(
                true,
                "Sinistro aprovado: todas as regras da apólice foram atendidas.",
                regras);
    }
}
