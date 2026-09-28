import blockchain.RegistradorBlockchain;
import contract.SmartContract;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import model.*;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;

/** API local do MVP: as regras e os blocos são executados no Java, não no navegador. */
public class ServidorSeguroChain {
  private static final Path WEB = Path.of("web").toAbsolutePath().normalize();
  private static final LocalChain LEDGER = new LocalChain(Path.of("data", "segurochain-ledger.tsv"));
  private static Seguradora SEGURADORA;
  private static int eventosReconstruidos;

  private static final Pattern MONEY = Pattern.compile("\\d{1,11}(\\.\\d{1,2})?");
  private static final Pattern PLATE = Pattern.compile("[A-Z]{3}-?\\d[A-Z0-9]\\d{2}");

  public static void main(String[] args) throws IOException {
    int port = args.length == 0 ? 8080 : Integer.parseInt(args[0]);
    reconstruirEstado();

    HttpServer server = HttpServer.create(new InetSocketAddress("localhost", port), 0);
    server.createContext("/api/state", e -> { if (only(e, "GET")) return; json(e, 200, stateJson()); });
    server.createContext("/api/ledger", ServidorSeguroChain::ledger);
    server.createContext("/api/difficulty", ServidorSeguroChain::difficulty);
    server.createContext("/api/policies", ServidorSeguroChain::policies);
    server.createContext("/api/claims", ServidorSeguroChain::claims);
    server.createContext("/", ServidorSeguroChain::staticFile);
    server.start();

    System.out.println("SeguroChain: http://localhost:" + port);
    System.out.println("Ledger local: " + LEDGER.file.toAbsolutePath());
    System.out.println("Estado reconstruído a partir de " + eventosReconstruidos + " eventos da blockchain"
        + (LEDGER.chain.isBlockChainValid() ? "." : " — ATENÇÃO: cadeia inválida, use \"Reparar cadeia\" na auditoria."));
  }

  private static void reconstruirEstado() {
    Seguradora seguradora = new Seguradora("SEG-001", "SeguroChain S.A.", "0xSEGURADORA", LEDGER);
    int aplicados = 0;

    for (Block b : LEDGER.chain.getBlocks()) {
      if (b.getIndex() == 0) continue;
      String[] parts = b.getData().split(" \\| ", 2);
      Map<String, String> dados = parts.length == 2 ? Json.parseFlat(parts[1]) : null;
      if (dados == null) continue;

      try {
        if (ReconstrucaoHistorico.aplicar(seguradora, parts[0], dados, b.getHash())) aplicados++;
      } catch (RuntimeException x) {
        System.err.println("Bloco #" + b.getIndex() + " ignorado na reconstrução: " + x.getMessage());
      }
    }

    SEGURADORA = seguradora;
    eventosReconstruidos = aplicados;
  }

  private static void ledger(HttpExchange e) throws IOException {
    String path = e.getRequestURI().getPath();
    if (path.equals("/api/ledger/repair")) {
      if (only(e, "POST")) return;
      try {
        int repaired = LEDGER.repair();
        reconstruirEstado();
        json(e, 200, "{\"repaired\":" + repaired + ",\"valid\":" + LEDGER.chain.isBlockChainValid() + "}");
      } catch (Exception x) { bad(e, x); }
      return;
    }
    if (only(e, "GET")) return;
    json(e, 200, LEDGER.json());
  }

  private static void difficulty(HttpExchange e) throws IOException {
    if ("GET".equals(e.getRequestMethod())) {
      json(e, 200, "{\"difficulty\":" + LEDGER.chain.getDifficulty() + "}");
      return;
    }
    if ("POST".equals(e.getRequestMethod())) {
      try {
        Map<String, String> f = form(e);
        int d = Integer.parseInt(required(f, "difficulty"));
        if (d < 1 || d > 6) throw new IllegalArgumentException("Dificuldade deve ser entre 1 e 6.");
        LEDGER.chain.setDifficulty(d);
        json(e, 200, "{\"difficulty\":" + d + "}");
      } catch (Exception x) { bad(e, x); }
      return;
    }
    e.getResponseHeaders().set("Allow", "GET, POST");
    e.sendResponseHeaders(405, -1);
  }

  private static void policies(HttpExchange e) throws IOException {
    if (only(e, "POST")) return;
    try {
      Map<String, String> f = form(e);
      LocalDate start = LocalDate.parse(required(f, "start"));
      LocalDate end = LocalDate.parse(required(f, "end"));
      String n = String.format("%04d", SEGURADORA.consultarApolices().size() + 1);

      String plate = required(f, "plate").toUpperCase();
      if (!PLATE.matcher(plate).matches()) throw new IllegalArgumentException("Placa inválida: use ABC-1234 ou ABC1D23.");
      String yearText = required(f, "year");
      int year = yearText.matches("\\d{4}") ? Integer.parseInt(yearText) : -1;
      if (year < 1950 || year > LocalDate.now().getYear() + 1) {
        throw new IllegalArgumentException("Ano do veículo inválido: use 4 dígitos entre 1950 e " + (LocalDate.now().getYear() + 1) + ".");
      }

      Cliente client = new Cliente("CLI-" + n, required(f, "client"), required(f, "wallet"));
      Veiculo vehicle = new Veiculo("VEI-" + n, plate, required(f, "vehicle"), year, "ID-" + UUID.randomUUID());

      Map<TipoCobertura, Boolean> covers = new EnumMap<>(TipoCobertura.class);
      for (TipoCobertura t : TipoCobertura.values()) {
        covers.put(t, Boolean.parseBoolean(f.getOrDefault("cover_" + t.name(), "false")));
      }
      if (!covers.containsValue(true)) throw new IllegalArgumentException("Selecione ao menos uma cobertura.");

      Apolice p = SEGURADORA.criarApolice("APL-" + n, client, vehicle, covers,
          money(f, "coverageValue", "Valor segurado"), money(f, "franchise", "Franquia"), start, end);
      json(e, 201, "{\"id\":\"" + Json.esc(p.getId()) + "\"}");
    } catch (Exception x) { bad(e, x); }
  }

  private static void claims(HttpExchange e) throws IOException {
    if (only(e, "POST")) return;
    String path = e.getRequestURI().getPath();
    try {
      if (path.equals("/api/claims")) {
        Map<String, String> f = form(e);
        Apolice p = policy(required(f, "policy"));
        Sinistro c = p.getCliente().abrirSinistro(p, TipoCobertura.valueOf(required(f, "type")),
            money(f, "amount", "Valor solicitado"), LocalDate.parse(required(f, "date")));
        json(e, 201, "{\"id\":\"" + Json.esc(c.getId()) + "\"}");
        return;
      }

      String[] part = path.split("/");
      if (part.length != 5) throw new IllegalArgumentException("Ação inexistente.");
      Sinistro c = claim(part[3]);
      if ("analyse".equals(part[4])) {
        if (c.getStatus() != StatusSinistro.EM_ANALISE) throw new IllegalStateException("Sinistro já foi decidido.");
        SEGURADORA.analisarSinistro(c);
      } else if ("pay".equals(part[4])) {
        SEGURADORA.registrarPagamento(c);
      } else {
        throw new IllegalArgumentException("Ação inexistente.");
      }
      json(e, 200, "{\"ok\":true}");
    } catch (Exception x) { bad(e, x); }
  }

  private static void staticFile(HttpExchange e) throws IOException {
    if (only(e, "GET")) return;
    String url = e.getRequestURI().getPath();
    if (url.equals("/")) url = "/index.html";
    Path file = WEB.resolve(url.substring(1)).normalize();
    if (!file.startsWith(WEB) || !Files.isRegularFile(file)) {
      e.sendResponseHeaders(404, -1);
      return;
    }
    byte[] body = Files.readAllBytes(file);
    String type = url.endsWith(".js") ? "text/javascript" : url.endsWith(".css") ? "text/css"
        : url.endsWith(".svg") ? "image/svg+xml" : "text/html";
    e.getResponseHeaders().set("Content-Type", type + "; charset=utf-8");
    e.getResponseHeaders().set("Cache-Control", "no-cache");
    e.sendResponseHeaders(200, body.length);
    try (OutputStream out = e.getResponseBody()) { out.write(body); }
  }

  private static String stateJson() {
    StringBuilder s = new StringBuilder("{\"policies\":[");
    List<Apolice> ps = SEGURADORA.consultarApolices();
    for (int i = 0; i < ps.size(); i++) {
      if (i > 0) s.append(',');
      Apolice p = ps.get(i);
      List<String> covers = new ArrayList<>();
      for (TipoCobertura t : TipoCobertura.values()) if (p.possuiCobertura(t)) covers.add(t.name());

      s.append("{\"id\":").append(Json.str(p.getId()))
          .append(",\"client\":").append(Json.str(p.getCliente().getNome()))
          .append(",\"wallet\":").append(Json.str(p.getCliente().getWallet()))
          .append(",\"vehicle\":").append(Json.str(p.getVeiculo().getModelo()))
          .append(",\"plate\":").append(Json.str(p.getVeiculo().getPlaca()))
          .append(",\"coverageValue\":").append(p.getValorSegurado().toPlainString())
          .append(",\"franchise\":").append(p.getFranquia().toPlainString())
          .append(",\"used\":").append(p.getValorComprometido().toPlainString())
          .append(",\"available\":").append(p.getSaldoDisponivel().toPlainString())
          .append(",\"start\":\"").append(p.getDataInicio())
          .append("\",\"end\":\"").append(p.getDataFim())
          .append("\",\"status\":\"").append(p.getStatus())
          .append("\",\"covers\":").append(Json.array(covers)).append('}');
    }

    s.append("],\"claims\":[");
    List<Sinistro> cs = SEGURADORA.consultarSinistros();
    for (int i = 0; i < cs.size(); i++) {
      if (i > 0) s.append(',');
      Sinistro c = cs.get(i);
      s.append("{\"id\":").append(Json.str(c.getId()))
          .append(",\"policyId\":").append(Json.str(c.getApolice().getId()))
          .append(",\"client\":").append(Json.str(c.getApolice().getCliente().getNome()))
          .append(",\"type\":\"").append(c.getTipo())
          .append("\",\"date\":\"").append(c.getData())
          .append("\",\"amount\":").append(c.getValorSolicitado().toPlainString())
          .append(",\"franchise\":").append(c.getFranquiaAplicada().toPlainString())
          .append(",\"indemnity\":").append(c.getValorIndenizacao().toPlainString())
          .append(",\"status\":\"").append(c.getStatus())
          .append("\",\"reason\":").append(Json.str(c.getMotivo()))
          .append(",\"rules\":").append(Json.array(c.getRegrasVerificadas()))
          .append(",\"hash\":").append(Json.str(c.getHashBlockchain())).append('}');
    }
    return s.append("],\"replayedEvents\":").append(eventosReconstruidos)
        .append(",\"rules\":{\"maxFranchisePct\":").append(Apolice.PERCENTUAL_MAXIMO_FRANQUIA)
        .append(",\"totalLossPct\":").append(SmartContract.PERCENTUAL_PERDA_TOTAL).append("}}").toString();
  }

  private static Map<String, String> form(HttpExchange e) throws IOException {
    String body = new String(e.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    Map<String, String> out = new HashMap<>();
    for (String pair : body.split("&")) {
      if (pair.isEmpty()) continue;
      String[] p = pair.split("=", 2);
      out.put(URLDecoder.decode(p[0], StandardCharsets.UTF_8), p.length > 1 ? URLDecoder.decode(p[1], StandardCharsets.UTF_8) : "");
    }
    return out;
  }

  private static String required(Map<String, String> f, String key) {
    String v = f.get(key);
    if (v == null || v.isBlank()) throw new IllegalArgumentException("Informe " + key + ".");
    return v.trim();
  }

  private static BigDecimal money(Map<String, String> f, String key, String label) {
    String v = f.get(key);
    if (v == null || v.isBlank()) throw new IllegalArgumentException("Informe " + label.toLowerCase() + ".");
    v = v.trim();
    if (!MONEY.matcher(v).matches()) {
      throw new IllegalArgumentException(label + " inválido: use apenas números com até 2 casas decimais.");
    }
    return new BigDecimal(v).setScale(2);
  }

  private static Apolice policy(String id) {
    Apolice p = SEGURADORA.buscarApolice(id);
    if (p == null) throw new IllegalArgumentException("Apólice não encontrada.");
    return p;
  }

  private static Sinistro claim(String id) {
    Sinistro c = SEGURADORA.buscarSinistro(id);
    if (c == null) throw new IllegalArgumentException("Sinistro não encontrado.");
    return c;
  }

  /** @return true se o método está errado (405 já enviado) */
  private static boolean only(HttpExchange e, String method) throws IOException {
    if (!e.getRequestMethod().equals(method)) {
      e.getResponseHeaders().set("Allow", method);
      e.sendResponseHeaders(405, -1);
      return true;
    }
    return false;
  }

  private static void json(HttpExchange e, int code, String s) throws IOException {
    byte[] b = s.getBytes(StandardCharsets.UTF_8);
    e.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
    e.sendResponseHeaders(code, b.length);
    try (OutputStream out = e.getResponseBody()) { out.write(b); }
  }

  private static void bad(HttpExchange e, Exception x) throws IOException {
    String msg = x instanceof NumberFormatException ? "Valor numérico inválido." : x.getMessage();
    json(e, 400, "{\"error\":" + Json.str(msg) + "}");
  }

  /**
   * Persiste cada bloco como linha local e reconstrói a cadeia no próximo boot.
   * Formato TSV: index \t timestamp \t data(base64) \t hash \t previousHash \t nonce
   */
  private static final class LocalChain implements RegistradorBlockchain {
    final Path file;
    final Blockchain chain = new Blockchain(2);

    LocalChain(Path source) { file = source; load(); }

    @Override
    public synchronized String registrarTransacao(String type, Map<String, Object> data) {
      Block block = chain.newBlock(type + " | " + Json.object(data));
      chain.addBlock(block);
      append(block);
      return block.getHash();
    }

    synchronized int repair() {
      int repaired = chain.repair();
      if (repaired > 0) rewriteAll();
      return repaired;
    }

    void load() {
      boolean hasGenesis = false;
      if (Files.exists(file)) {
        try {
          for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (line.isBlank()) continue;
            String[] p = line.split("\\t");
            // Suporte a formato antigo (5 colunas) e novo (6 colunas com nonce)
            if (p.length < 5) continue;
            int index = Integer.parseInt(p[0]);
            long timestamp = Long.parseLong(p[1]);
            String payload = new String(Base64.getDecoder().decode(p[2]), StandardCharsets.UTF_8);
            int nonce = p.length >= 6 ? Integer.parseInt(p[5]) : 0;

            // Reconstrução fiel: usa o construtor que preserva todos os campos originais
            Block b = new Block(index, timestamp, p[4], payload, nonce, p[3]);
            if (index == 0 && !hasGenesis) {
              chain.replaceGenesis(b);
              hasGenesis = true;
            } else {
              chain.addReconstructedBlock(b);
            }
          }
        } catch (Exception e) {
          System.err.println("Ledger local inválido; mantendo os blocos que puderam ser lidos.");
          e.printStackTrace();
        }
      }
      if (!hasGenesis) rewriteAll();
    }

    private static String line(Block b) {
      return b.getIndex() + "\t" + b.getTimestamp() + "\t"
          + Base64.getEncoder().encodeToString(b.getData().getBytes(StandardCharsets.UTF_8))
          + "\t" + b.getHash() + "\t" + b.getPreviousHash() + "\t" + b.getNonce()
          + System.lineSeparator();
    }

    void append(Block b) {
      try {
        Files.createDirectories(file.getParent());
        Files.writeString(file, line(b), StandardCharsets.UTF_8,
            java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
      } catch (IOException x) {
        throw new IllegalStateException("Não foi possível salvar o ledger local.", x);
      }
    }

    void rewriteAll() {
      try {
        Files.createDirectories(file.getParent());
        StringBuilder all = new StringBuilder();
        for (Block b : chain.getBlocks()) all.append(line(b));
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, all.toString(), StandardCharsets.UTF_8);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
      } catch (IOException x) {
        throw new IllegalStateException("Não foi possível salvar o ledger local.", x);
      }
    }

    synchronized String json() {
      StringBuilder s = new StringBuilder();
      s.append("{\"valid\":").append(chain.isBlockChainValid());
      s.append(",\"difficulty\":").append(chain.getDifficulty());
      s.append(",\"blocks\":[");

      List<Block> allBlocks = chain.getBlocks();
      for (int i = 0; i < allBlocks.size(); i++) {
        if (i > 0) s.append(",");
        Block b = allBlocks.get(i);
        String error = chain.validationError(i);
        s.append("{\"index\":").append(b.getIndex());
        s.append(",\"timestamp\":").append(b.getTimestamp());
        s.append(",\"data\":").append(Json.str(b.getData()));
        s.append(",\"hash\":").append(Json.str(b.getHash()));
        s.append(",\"previousHash\":").append(Json.str(b.getPreviousHash()));
        s.append(",\"nonce\":").append(b.getNonce());
        s.append(",\"valid\":").append(error == null);
        s.append(",\"error\":").append(Json.str(error));
        s.append("}");
      }

      return s.append("]}").toString();
    }
  }

  static final class Json {
    static String esc(String v) {
      if (v == null) return "";
      StringBuilder s = new StringBuilder();
      for (char c : v.toCharArray()) {
        switch (c) {
          case '"': s.append("\\\""); break;
          case '\\': s.append("\\\\"); break;
          case '\n': s.append("\\n"); break;
          case '\r': s.append("\\r"); break;
          case '\t': s.append("\\t"); break;
          default:
            if (c < 0x20) s.append(String.format("\\u%04x", (int) c));
            else s.append(c);
        }
      }
      return s.toString();
    }

    static String str(String v) {
      return v == null ? "null" : "\"" + esc(v) + "\"";
    }

    static String array(List<String> values) {
      StringBuilder s = new StringBuilder("[");
      for (int i = 0; i < values.size(); i++) {
        if (i > 0) s.append(',');
        s.append(str(values.get(i)));
      }
      return s.append(']').toString();
    }

    static String object(Map<String, Object> m) {
      StringBuilder s = new StringBuilder("{");
      boolean first = true;
      for (Map.Entry<String, Object> en : m.entrySet()) {
        if (!first) s.append(',');
        first = false;
        Object v = en.getValue();
        s.append(str(en.getKey())).append(':');
        if (v instanceof BigDecimal) s.append(((BigDecimal) v).toPlainString());
        else if (v instanceof Number || v instanceof Boolean) s.append(v);
        else s.append(str(v == null ? null : v.toString()));
      }
      return s.append('}').toString();
    }

    static Map<String, String> parseFlat(String json) {
      Map<String, String> out = new LinkedHashMap<>();
      int[] i = {skip(json, 0)};
      if (i[0] >= json.length() || json.charAt(i[0]) != '{') return null;
      i[0] = skip(json, i[0] + 1);
      if (i[0] < json.length() && json.charAt(i[0]) == '}') return out;

      while (i[0] < json.length()) {
        if (json.charAt(i[0]) != '"') return null;
        String key = readString(json, i);
        if (key == null) return null;
        i[0] = skip(json, i[0]);
        if (i[0] >= json.length() || json.charAt(i[0]) != ':') return null;
        i[0] = skip(json, i[0] + 1);

        String value;
        if (i[0] < json.length() && json.charAt(i[0]) == '"') {
          value = readString(json, i);
          if (value == null) return null;
        } else {
          int start = i[0];
          while (i[0] < json.length() && json.charAt(i[0]) != ',' && json.charAt(i[0]) != '}') i[0]++;
          value = json.substring(start, i[0]).trim();
          if (value.equals("null")) value = null;
        }
        out.put(key, value);

        i[0] = skip(json, i[0]);
        if (i[0] >= json.length()) return null;
        char c = json.charAt(i[0]);
        if (c == '}') return out;
        if (c != ',') return null;
        i[0] = skip(json, i[0] + 1);
      }
      return null;
    }

    private static int skip(String s, int i) {
      while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
      return i;
    }

    private static String readString(String s, int[] i) {
      StringBuilder out = new StringBuilder();
      int p = i[0] + 1;
      while (p < s.length()) {
        char c = s.charAt(p++);
        if (c == '"') { i[0] = p; return out.toString(); }
        if (c != '\\') { out.append(c); continue; }
        if (p >= s.length()) return null;
        char e = s.charAt(p++);
        switch (e) {
          case 'n': out.append('\n'); break;
          case 'r': out.append('\r'); break;
          case 't': out.append('\t'); break;
          case 'u':
            if (p + 4 > s.length()) return null;
            out.append((char) Integer.parseInt(s.substring(p, p + 4), 16));
            p += 4;
            break;
          default: out.append(e);
        }
      }
      return null;
    }
  }
}
