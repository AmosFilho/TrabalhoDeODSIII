package contract;

import model.Apolice;
import model.Sinistro;
import model.TipoCobertura;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

    public static final int PERCENTUAL_PERDA_TOTAL = 75;

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

        BigDecimal valorSolicitado = sinistro.getValorSolicitado();
        BigDecimal limitePerdaTotal = apolice.getValorSegurado()
                .multiply(BigDecimal.valueOf(PERCENTUAL_PERDA_TOTAL))
                .divide(BigDecimal.valueOf(100));
        BigDecimal franquia;
        if (sinistro.getTipo() == TipoCobertura.ROUBO) {
            franquia = BigDecimal.ZERO;
            regras.add("✓ Franquia não se aplica: roubo é indenizado integralmente");
        } else if (valorSolicitado.compareTo(limitePerdaTotal) >= 0) {
            franquia = BigDecimal.ZERO;
            regras.add("✓ Franquia não se aplica: perda total (prejuízo de " + brl(valorSolicitado)
                    + " ≥ " + PERCENTUAL_PERDA_TOTAL + "% do valor segurado)");
        } else {
            franquia = apolice.getFranquia();
            if (valorSolicitado.compareTo(franquia) <= 0) {
                regras.add("✗ Prejuízo (" + brl(valorSolicitado) + ") não supera a franquia ("
                        + brl(franquia) + ")");
                return new ResultadoAnalise(
                        false,
                        "Dano parcial de " + brl(valorSolicitado) + " fica abaixo da franquia de "
                                + brl(franquia) + ": o valor fica por conta do segurado e não há indenização.",
                        regras,
                        franquia,
                        BigDecimal.ZERO);
            }
            regras.add("✓ Prejuízo (" + brl(valorSolicitado) + ") supera a franquia (" + brl(franquia) + ")");
        }

        BigDecimal indenizacao = valorSolicitado.subtract(franquia);
        BigDecimal saldo = apolice.getSaldoDisponivel();
        if (indenizacao.compareTo(saldo) > 0) {
            regras.add("✗ Indenização (" + brl(indenizacao) + ") excede o saldo da apólice (" + brl(saldo) + ")");
            return new ResultadoAnalise(
                    false,
                    "A indenização de " + brl(indenizacao) + " excede o saldo disponível da apólice ("
                            + brl(saldo) + " de " + brl(apolice.getValorSegurado()) + ").",
                    regras,
                    franquia,
                    BigDecimal.ZERO);
        }
        regras.add("✓ Indenização (" + brl(indenizacao) + ") cabe no saldo da apólice (" + brl(saldo) + ")");

        // Todas as regras passaram -> aprova
        String calculo = franquia.signum() == 0
                ? "sem franquia"
                : brl(valorSolicitado) + " solicitados - " + brl(franquia) + " de franquia";
        return new ResultadoAnalise(
                true,
                "Sinistro aprovado: indenização de " + brl(indenizacao) + " (" + calculo + ").",
                regras,
                franquia,
                indenizacao);
    }

    private static String brl(BigDecimal valor) {
        return NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(valor);
    }
}
