package model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class ReconstrucaoHistorico {

    private ReconstrucaoHistorico() {
    }

    public static boolean aplicar(Seguradora seguradora, String tipoEvento, Map<String, String> d, String hash) {
        switch (tipoEvento) {
            case "APOLICE_EMITIDA": return apoliceEmitida(seguradora, d);
            case "SINISTRO_ABERTO": return sinistroAberto(seguradora, d, hash);
            case "ANALISE_SINISTRO": return analiseSinistro(seguradora, d, hash);
            case "PAGAMENTO_SINISTRO": return pagamentoSinistro(seguradora, d, hash);
            default: return false;
        }
    }

    private static boolean apoliceEmitida(Seguradora seguradora, Map<String, String> d) {
        if (seguradora.buscarApolice(d.get("idApolice")) != null) {
            return false;
        }

        Cliente cliente = new Cliente(d.get("idCliente"), d.get("cliente"), d.get("wallet"));
        Veiculo veiculo = new Veiculo(d.get("idVeiculo"), d.get("placa"), d.get("modelo"),
                Integer.parseInt(d.get("ano")), d.get("identificador"));

        Map<TipoCobertura, Boolean> coberturas = new EnumMap<>(TipoCobertura.class);
        List<String> cobertas = Arrays.asList(d.getOrDefault("coberturas", "").split(","));
        for (TipoCobertura t : TipoCobertura.values()) {
            coberturas.put(t, cobertas.contains(t.name()));
        }

        Apolice apolice = new Apolice(d.get("idApolice"), cliente, veiculo, seguradora, coberturas,
                new BigDecimal(d.get("valorSegurado")), new BigDecimal(d.getOrDefault("franquia", "0")),
                LocalDate.parse(d.get("inicio")), LocalDate.parse(d.get("fim")), StatusApolice.ATIVA);
        seguradora.restaurarApolice(apolice);
        return true;
    }

    private static boolean sinistroAberto(Seguradora seguradora, Map<String, String> d, String hash) {
        Apolice apolice = seguradora.buscarApolice(d.get("idApolice"));
        if (apolice == null || seguradora.buscarSinistro(d.get("idSinistro")) != null) {
            return false;
        }

        Sinistro sinistro = new Sinistro(d.get("idSinistro"), apolice, TipoCobertura.valueOf(d.get("tipo")),
                new BigDecimal(d.get("valorSolicitado")), LocalDate.parse(d.get("data")));
        sinistro.registrarHashBlockchain(hash);
        seguradora.restaurarSinistro(sinistro);
        return true;
    }

    private static boolean analiseSinistro(Seguradora seguradora, Map<String, String> d, String hash) {
        Sinistro sinistro = seguradora.buscarSinistro(d.get("idSinistro"));
        if (sinistro == null || sinistro.getStatus() != StatusSinistro.EM_ANALISE) {
            return false;
        }

        boolean aprovado = Boolean.parseBoolean(d.get("aprovado"));
        sinistro.atualizarStatus(aprovado ? StatusSinistro.APROVADO : StatusSinistro.REJEITADO, d.get("motivo"));
        sinistro.registrarCalculo(new BigDecimal(d.getOrDefault("franquia", "0")),
                new BigDecimal(d.getOrDefault("valorIndenizacao", "0")),
                d.getOrDefault("regras", "").isBlank() ? List.of() : Arrays.asList(d.get("regras").split("\n")));
        sinistro.registrarHashBlockchain(hash);
        return true;
    }

    private static boolean pagamentoSinistro(Seguradora seguradora, Map<String, String> d, String hash) {
        Sinistro sinistro = seguradora.buscarSinistro(d.get("idSinistro"));
        if (sinistro == null || sinistro.getStatus() != StatusSinistro.APROVADO) {
            return false;
        }

        sinistro.atualizarStatus(StatusSinistro.PAGO, sinistro.getMotivo());
        sinistro.registrarHashBlockchain(hash);
        return true;
    }
}
