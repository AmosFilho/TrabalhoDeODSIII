package model;

/**
 * Bem protegido pela apólice. Fica associado ao Cliente indiretamente,
 * através da Apolice (uma Apolice liga um Cliente a um Veiculo).
 */
public class Veiculo {

    private final String id;
    private final String placa;
    private final String modelo;
    private final int ano;

    // Ex.: chassi ou RENAVAM. Também pode ser reaproveitado como o
    // identificador único do bem quando os dados forem levados para a
    // blockchain (útil para localizar todos os blocos relacionados a este
    // veículo específico).
    private final String identificador;

    public Veiculo(String id, String placa, String modelo, int ano, String identificador) {
        this.id = id;
        this.placa = placa;
        this.modelo = modelo;
        this.ano = ano;
        this.identificador = identificador;
    }

    public String getId() { return id; }
    public String getPlaca() { return placa; }
    public String getModelo() { return modelo; }
    public int getAno() { return ano; }
    public String getIdentificador() { return identificador; }

    @Override
    public String toString() {
        return "Veículo #" + id + "\n" +
                "Placa: " + placa + "\n" +
                "Modelo: " + modelo + "\n" +
                "Ano: " + ano;
    }
}
