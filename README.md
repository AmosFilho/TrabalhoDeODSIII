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

Os dados de auditoria ficam em `data/segurochain-ledger.tsv`. Esse arquivo é
recarregado ao reiniciar o servidor e os blocos, hashes e hashes anteriores
podem ser vistos na seção **Auditoria blockchain** da aplicação.

