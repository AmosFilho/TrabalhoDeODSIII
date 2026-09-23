import blockchain.RegistradorBlockchain;
import blockchain.RegistradorBlockchainSimulado;

import contract.ResultadoAnalise;

import model.Apolice;
import model.Cliente;
import model.Seguradora;
import model.Sinistro;
import model.TipoCobertura;
import model.Veiculo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;

/**
 * Demonstra o fluxo completo descrito no enunciado:
 *
 * Seguradora cria apólice -> Cliente sofre acidente -> abre sinistro
 * -> SmartContract analisa -> aprova/rejeita -> (se aprovado) paga.
 *
 * Troque RegistradorBlockchainSimulado por uma implementação real assim
 * que o módulo de Blockchain/Block estiver plugado (ver o pacote
 * com.segurochain.blockchain).
 */
public class Main {

    public static void main(String[] args) {

        RegistradorBlockchain registrador = new RegistradorBlockchainSimulado();
        Seguradora seguradora = new Seguradora("1", "SeguroChain S.A.", "0xSEGURADORA...", registrador);

        Cliente joao = new Cliente("1", "João", "0xJOAO...");
        Veiculo corolla = new Veiculo("001", "ABC-1234", "Toyota Corolla", 2024, "CHASSI-XYZ");

        Map<TipoCobertura, Boolean> coberturas = new EnumMap<>(TipoCobertura.class);
        coberturas.put(TipoCobertura.COLISAO, true);
        coberturas.put(TipoCobertura.ROUBO, true);
        coberturas.put(TipoCobertura.INCENDIO, false);

        Apolice apolice = seguradora.criarApolice(
                "001", joao, corolla, coberturas,
                new BigDecimal("80000"),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2027, 1, 1));

        System.out.println(apolice);
        System.out.println();

        // Cliente sofre um acidente e abre um sinistro de colisão
        Sinistro sinistro = joao.abrirSinistro(
                apolice, TipoCobertura.COLISAO, new BigDecimal("15000"), LocalDate.of(2026, 9, 10));

        System.out.println(sinistro);
        System.out.println();

        // Seguradora manda o SmartContract analisar (nunca decide sozinha)
        ResultadoAnalise resultado = seguradora.analisarSinistro(sinistro);

        System.out.println("Regras verificadas pelo SmartContract:");
        resultado.getRegrasVerificadas().forEach(System.out::println);
        System.out.println();

        System.out.println(sinistro);
        System.out.println();

        if (resultado.isAprovado()) {
            seguradora.registrarPagamento(sinistro);
        }

        System.out.println(sinistro);
        System.out.println();

        System.out.println("Histórico de sinistros do cliente:");
        joao.consultarHistorico().forEach(System.out::println);
    }
}
