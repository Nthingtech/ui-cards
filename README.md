# ui-cards

Digite um pensamento e ele vira um card: evento, lembrete, lista, timer, divisão de conta ou nota.
Recriação em português do tutorial [Shapeshift do The Main Thread](https://www.the-main-thread.com/p/quarkus-shapeshift-ollama),
usando a Decisions API da OpenAI no lugar do Ollama.

- **OpenAI (`gpt-6-luna`)** só escolhe o tipo de card e diz se é urgente, com probabilidades.
- **Java** extrai os campos (datas, horas, durações, valores) sem IA, no `CardParser`.
- **O navegador** mostra o card editável e salva no `localStorage`.

## Rodar

1. Crie um arquivo `.env` na raiz do projeto (ele já está no `.gitignore`):

   ```
   OPENAI_API_KEY=sua-chave-aqui
   ```

2. Suba o app:

   ```bash
   ./mvnw quarkus:dev
   ```

3. Abra <http://127.0.0.1:8098>.

Sem a chave, o app sobe do mesmo jeito: a classificação automática fica desligada, e você escolhe o card manualmente.

## Testes

```bash
./mvnw test
```

Os testes usam um servidor falso da OpenAI (`OpenAiStub`), então não precisam de chave nem geram custo.