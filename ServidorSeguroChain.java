import blockchain.RegistradorBlockchain;
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
import java.time.LocalDate;
import java.util.*;

/** API local do MVP: as regras e os blocos são executados no Java, não no navegador. */
public class ServidorSeguroChain {
  private static final Path WEB = Path.of("web").toAbsolutePath().normalize();
  private static final List<Cliente> CLIENTES = new ArrayList<>();
  private static final LocalChain LEDGER = new LocalChain(Path.of("data", "segurochain-ledger.tsv"));
  private static final Seguradora SEGURADORA = new Seguradora("SEG-001", "SeguroChain S.A.", "0xSEGURADORA", LEDGER);

  public static void main(String[] args) throws IOException {
    int port = args.length == 0 ? 8080 : Integer.parseInt(args[0]);
    HttpServer server = HttpServer.create(new InetSocketAddress("localhost", port), 0);
    server.createContext("/api/state", e -> { if(only(e, "GET")) return; json(e, 200, stateJson()); });
    server.createContext("/api/ledger", e -> { if(only(e, "GET")) return; json(e, 200, LEDGER.json()); });
    server.createContext("/api/difficulty", ServidorSeguroChain::difficulty);
    server.createContext("/api/policies", ServidorSeguroChain::policies);
    server.createContext("/api/claims", ServidorSeguroChain::claims);
    server.createContext("/", ServidorSeguroChain::staticFile);
    server.start();
    System.out.println("SeguroChain: http://localhost:" + port);
    System.out.println("Ledger local: " + LEDGER.file.toAbsolutePath());
  }

  private static void difficulty(HttpExchange e) throws IOException {
    if ("GET".equals(e.getRequestMethod())) {
      json(e, 200, "{\"difficulty\":" + LEDGER.chain.getDifficulty() + "}");
      return;
    }
    if ("POST".equals(e.getRequestMethod())) {
      try {
        Map<String,String> f = form(e);
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
    if (!"POST".equals(e.getRequestMethod())) { only(e, "POST"); return; }
    try {
      Map<String,String> f = form(e); LocalDate start = LocalDate.parse(required(f,"start")), end = LocalDate.parse(required(f,"end"));
      if (end.isBefore(start)) throw new IllegalArgumentException("A vigência final deve ser posterior à inicial.");
      Cliente client = new Cliente(next("CLI"), required(f,"client"), required(f,"wallet"));
      Veiculo vehicle = new Veiculo(next("VEI"), required(f,"plate").toUpperCase(), required(f,"vehicle"), Integer.parseInt(required(f,"year")), "ID-" + UUID.randomUUID());
      Map<TipoCobertura,Boolean> covers = new EnumMap<>(TipoCobertura.class);
      for (TipoCobertura t : TipoCobertura.values()) covers.put(t, Boolean.parseBoolean(f.getOrDefault("cover_" + t.name(), "false")));
      SEGURADORA.criarApolice(next("APL"), client, vehicle, covers, new BigDecimal(required(f,"coverageValue")), start, end); CLIENTES.add(client);
      json(e, 201, "{\"ok\":true}");
    } catch (Exception x) { bad(e,x); }
  }

  private static void claims(HttpExchange e) throws IOException {
    String path = e.getRequestURI().getPath();
    if ("POST".equals(e.getRequestMethod()) && path.equals("/api/claims")) try {
      Map<String,String> f=form(e); Apolice p=policy(required(f,"policy"));
      Sinistro c=p.getCliente().abrirSinistro(p, TipoCobertura.valueOf(required(f,"type")), new BigDecimal(required(f,"amount")), LocalDate.parse(required(f,"date")));
      json(e,201,"{\"id\":\""+esc(c.getId())+"\"}"); return;
    } catch(Exception x) { bad(e,x); return; }
    String[] part=path.split("/");
    if ("POST".equals(e.getRequestMethod()) && part.length==5) try {
      Sinistro c=claim(part[3]);
      if ("analyse".equals(part[4])) { if(c.getStatus()!=StatusSinistro.EM_ANALISE) throw new IllegalStateException("Sinistro já foi decidido."); SEGURADORA.analisarSinistro(c); }
      else if ("pay".equals(part[4])) SEGURADORA.registrarPagamento(c); else throw new IllegalArgumentException("Ação inexistente.");
      json(e,200,"{\"ok\":true}"); return;
    } catch(Exception x) { bad(e,x); return; }
    only(e,"POST");
  }

  private static String stateJson() {
    StringBuilder s=new StringBuilder("{\"policies\":["); List<Apolice> ps=SEGURADORA.consultarApolices();
    for(int i=0;i<ps.size();i++){ if(i>0)s.append(','); Apolice p=ps.get(i); s.append("{\"id\":\"").append(esc(p.getId())).append("\",\"client\":\"").append(esc(p.getCliente().getNome())).append("\",\"wallet\":\"").append(esc(p.getCliente().getWallet())).append("\",\"vehicle\":\"").append(esc(p.getVeiculo().getModelo())).append("\",\"plate\":\"").append(esc(p.getVeiculo().getPlaca())).append("\",\"coverageValue\":").append(p.getValorSegurado()).append(",\"start\":\"").append(p.getDataInicio()).append("\",\"end\":\"").append(p.getDataFim()).append("\",\"status\":\"").append(p.getStatus()).append("\",\"covers\":["); boolean first=true; for(TipoCobertura t:TipoCobertura.values())if(p.possuiCobertura(t)){if(!first)s.append(',');s.append("\"").append(t).append("\"");first=false;}s.append("]}"); }
    s.append("],\"claims\":["); List<Sinistro> cs=SEGURADORA.consultarSinistros();
    for(int i=0;i<cs.size();i++){if(i>0)s.append(',');Sinistro c=cs.get(i);s.append("{\"id\":\"").append(esc(c.getId())).append("\",\"policyId\":\"").append(esc(c.getApolice().getId())).append("\",\"client\":\"").append(esc(c.getApolice().getCliente().getNome())).append("\",\"type\":\"").append(c.getTipo()).append("\",\"date\":\"").append(c.getData()).append("\",\"amount\":").append(c.getValorSolicitado()).append(",\"status\":\"").append(c.getStatus()).append("\",\"reason\":\"").append(esc(c.getMotivo())).append("\"}");} 
    return s.append("]}").toString();
  }
  private static void staticFile(HttpExchange e)throws IOException{if(!"GET".equals(e.getRequestMethod())){only(e,"GET");return;}String url=e.getRequestURI().getPath();if(url.equals("/"))url="/index.html";Path file=WEB.resolve(url.substring(1)).normalize();if(!file.startsWith(WEB)||!Files.isRegularFile(file)){e.sendResponseHeaders(404,-1);return;}byte[] body=Files.readAllBytes(file);String type=url.endsWith(".js")?"text/javascript":url.endsWith(".css")?"text/css":url.endsWith(".svg")?"image/svg+xml":"text/html";e.getResponseHeaders().set("Content-Type",type+"; charset=utf-8");e.sendResponseHeaders(200,body.length);try(OutputStream out=e.getResponseBody()){out.write(body);}}
  private static Map<String,String> form(HttpExchange e)throws IOException{String body=new String(e.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);Map<String,String> out=new HashMap<>();for(String pair:body.split("&")){if(pair.isEmpty())continue;String[] p=pair.split("=",2);out.put(URLDecoder.decode(p[0],StandardCharsets.UTF_8),p.length>1?URLDecoder.decode(p[1],StandardCharsets.UTF_8):"");}return out;}
  private static String required(Map<String,String> f,String key){String v=f.get(key);if(v==null||v.isBlank())throw new IllegalArgumentException("Informe "+key+".");return v.trim();}
  private static Apolice policy(String id){return SEGURADORA.consultarApolices().stream().filter(p->p.getId().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("Apólice não encontrada."));}
  private static Sinistro claim(String id){return SEGURADORA.consultarSinistros().stream().filter(c->c.getId().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("Sinistro não encontrado."));}
  private static String next(String prefix){return prefix+"-"+String.format("%04d",SEGURADORA.consultarApolices().size()+SEGURADORA.consultarSinistros().size()+CLIENTES.size()+1);}
  private static String esc(String v){return v==null?"":v.replace("\\","\\\\").replace("\"","\\\"").replace("\n"," ").replace("\r"," ");}
  /** @return true se o método está errado (405 já enviado) */
  private static boolean only(HttpExchange e,String method)throws IOException{if(!e.getRequestMethod().equals(method)){e.getResponseHeaders().set("Allow",method);e.sendResponseHeaders(405,-1);return true;}return false;}
  private static void json(HttpExchange e,int code,String s)throws IOException{byte[] b=s.getBytes(StandardCharsets.UTF_8);e.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");e.sendResponseHeaders(code,b.length);try(OutputStream out=e.getResponseBody()){out.write(b);}}
  private static void bad(HttpExchange e,Exception x)throws IOException{json(e,400,"{\"error\":\""+esc(x.getMessage())+"\"}");}

  /**
   * Persiste cada bloco como linha local e reconstrói a cadeia no próximo boot.
   * Formato TSV: index \t timestamp \t data(base64) \t hash \t previousHash \t nonce
   */
  private static final class LocalChain implements RegistradorBlockchain {
    final Path file;
    final Blockchain chain = new Blockchain(2);
    final List<LedgerRecord> records = new ArrayList<>();

    LocalChain(Path source) { file = source; load(); }

    public synchronized String registrarTransacao(String type, Map<String, Object> data) {
      String payload = type + " | " + data;
      Block block = chain.newBlock(payload);
      chain.addBlock(block);
      LedgerRecord r = new LedgerRecord(block.getIndex(), block.getTimestamp(), payload, block.getHash(), block.getPreviousHash(), block.getNonce());
      records.add(r);
      persist(r);
      return block.getHash();
    }

    void load() {
      if (!Files.exists(file)) return;
      try {
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
          if (line.isBlank()) continue;
          String[] p = line.split("\\t");
          // Suporte a formato antigo (5 colunas) e novo (6 colunas com nonce)
          if (p.length < 5) continue;
          int index = Integer.parseInt(p[0]);
          long timestamp = Long.parseLong(p[1]);
          String payload = new String(Base64.getDecoder().decode(p[2]), StandardCharsets.UTF_8);
          String hash = p[3];
          String previousHash = p[4];
          int nonce = p.length >= 6 ? Integer.parseInt(p[5]) : 0;

          // Reconstrução fiel: usa o construtor que preserva todos os campos originais
          Block b = new Block(index, timestamp, previousHash, payload, nonce, hash);
          chain.addReconstructedBlock(b);
          records.add(new LedgerRecord(index, timestamp, payload, hash, previousHash, nonce));
        }
      } catch (Exception e) {
        System.err.println("Ledger local inválido; mantendo os blocos que puderam ser lidos.");
        e.printStackTrace();
      }
    }

    void persist(LedgerRecord r) {
      try {
        Files.createDirectories(file.getParent());
        String line = r.index + "\t" + r.timestamp + "\t"
            + Base64.getEncoder().encodeToString(r.data.getBytes(StandardCharsets.UTF_8))
            + "\t" + r.hash + "\t" + r.previousHash + "\t" + r.nonce
            + System.lineSeparator();
        Files.writeString(file, line, StandardCharsets.UTF_8,
            java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
      } catch (IOException x) {
        throw new IllegalStateException("Não foi possível salvar o ledger local.", x);
      }
    }

    String json() {
      StringBuilder s = new StringBuilder();
      s.append("{\"valid\":").append(chain.isBlockChainValid());
      s.append(",\"difficulty\":").append(chain.getDifficulty());
      s.append(",\"blocks\":[");

      List<Block> allBlocks = chain.getBlocks();
      for (int i = 0; i < allBlocks.size(); i++) {
        if (i > 0) s.append(",");
        Block b = allBlocks.get(i);
        s.append("{\"index\":").append(b.getIndex());
        s.append(",\"timestamp\":").append(b.getTimestamp());
        s.append(",\"data\":\"").append(esc(b.getData())).append("\"");
        s.append(",\"hash\":\"").append(b.getHash()).append("\"");
        s.append(",\"previousHash\":\"").append(esc(b.getPreviousHash())).append("\"");
        s.append(",\"nonce\":").append(b.getNonce());
        s.append("}");
      }

      return s.append("]}").toString();
    }
  }
  private record LedgerRecord(int index, long timestamp, String data, String hash, String previousHash, int nonce) {}
}
