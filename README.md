# TrabalhoDeODSIII

## MVP web — SeguroChain

O front-end do MVP está em `web/` e implementa o fluxo completo de seguros:

1. emissão de apólice com cliente, veículo, vigência, coberturas e limite;
2. abertura de sinistro;
3. análise automática das regras de vigência, cobertura e valor pelo Smart Contract simulado;
4. aprovação ou rejeição com as regras verificadas;
5. registro do pagamento para sinistros aprovados e trilha de auditoria com hash blockchain simulado.

Para rodar localmente, na raiz do repositório execute:

```powershell
.\scripts\run-mvp.ps1
```

Depois, acesse [http://localhost:8080](http://localhost:8080). O script compila e inicia o servidor Java local, sem dependências externas.

O front-end usa a API local; assim, a emissão de apólices e a análise de
sinistros passam pelas entidades Java existentes (`Seguradora`, `Apolice`,
`SmartContract` e `Sinistro`). Os eventos `ANALISE_SINISTRO` e
`PAGAMENTO_SINISTRO` são incluídos pela implementação local de
`RegistradorBlockchain`, que chama `Blockchain.newBlock` e
`Blockchain.addBlock` do repositório.

### Persistência pela blockchain

A blockchain é a única fonte de dados persistida, em `data/segurochain-ledger.tsv`
(incluindo o bloco gênesis). Todo evento vira um bloco com payload
`TIPO | {json}`:

| Evento | Quando |
|---|---|
| `APOLICE_EMITIDA` | emissão da apólice (cliente, veículo, coberturas, valor segurado, franquia, vigência) |
| `SINISTRO_ABERTO` | abertura do sinistro |
| `ANALISE_SINISTRO` | decisão do Smart Contract (regras, franquia, indenização, motivo) |
| `PAGAMENTO_SINISTRO` | pagamento da indenização |

Ao iniciar, o servidor relê os blocos em ordem e reconstrói apólices e
sinistros (`model/ReconstrucaoHistorico`). Na tela de decisão de cada sinistro
aparece o histórico de blocos relacionados a ele.

### Regras do Smart Contract

1. a apólice existe;
2. está vigente na data do sinistro;
3. a cobertura contempla o tipo de sinistro;
4. a **franquia** se aplica? Não em **roubo** nem em **perda total** (prejuízo
   ≥ 75% do valor segurado): nesses casos a indenização é integral. Em dano
   parcial, o prejuízo precisa superar a franquia;
5. a indenização (prejuízo − franquia aplicada) cabe no **saldo** da apólice.
   O valor segurado é o limite total: indenizações aprovadas ou pagas consomem
   esse saldo.

Na emissão, a franquia pode ser no máximo 20% do valor segurado.

### Formato dos valores

Os campos de dinheiro aceitam só dígitos e se formatam sozinhos
(`R$ 1.234,56`, com os centavos entrando pela direita). A API recebe os valores
sempre como `1234.56` e recusa vírgula, separador de milhar, notação científica
ou mais de 2 casas decimais. O ano do veículo deve ter 4 dígitos e a placa segue
o padrão antigo (`ABC-1234`) ou Mercosul (`ABC1D23`).

### Integridade e reparo da cadeia

A seção **Auditoria blockchain** valida bloco a bloco e mostra o motivo de cada
bloco inválido (hash que não confere com o conteúdo, hash anterior quebrado ou
índice fora de sequência). O botão **Reparar cadeia** reencadeia e reminera o
primeiro bloco inválido e todos os seguintes, mantendo os dados de cada bloco.

