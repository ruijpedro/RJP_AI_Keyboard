# Atualizações automáticas dos dicionários

A V1.1 mantém o corretor local e acrescenta um feed remoto no próprio repositório GitHub.

## Como funciona

1. O APK é compilado pelo workflow `Build Android APK`.
2. O workflow injeta automaticamente no APK o URL:
   `https://raw.githubusercontent.com/<OWNER>/<REPO>/<BRANCH>/dictionary-feed/manifest.json`
3. A app agenda uma verificação periódica (aproximadamente diária, conforme o JobScheduler do Android).
4. No arranque também pode verificar, no máximo uma vez a cada 6 horas.
5. Só são descarregados ficheiros cuja versão mudou.
6. Cada ficheiro é validado por formato e SHA-256 antes de substituir a versão anterior.
7. Se não houver Internet ou ocorrer um erro, o corretor continua a usar a última versão válida ou o dicionário incluído no APK.

## Atualização do feed no GitHub

O workflow `Update Runtime Dictionaries` corre semanalmente à segunda-feira e também pode ser lançado manualmente em Actions. Ele:

- descarrega as fontes Hunspell abertas;
- combina-as com o vocabulário curado da app;
- gera os seis TSV do feed;
- cria `manifest.json` com SHA-256 e versão por conteúdo;
- faz commit apenas se existirem alterações.

## Importante

Para o URL automático funcionar sem configuração manual, compila o APK através do GitHub Actions incluído neste projeto. Um build local sem `-PDICTIONARY_MANIFEST_URL=...` mantém os dicionários internos e mostra que o feed não está configurado.

## Repositório privado

O URL `raw.githubusercontent.com` usado automaticamente pelo workflow é acessível sem autenticação apenas quando o feed está público. Se o repositório principal for privado, compila com `-PDICTIONARY_MANIFEST_URL=<URL HTTPS público>` apontando para um repositório/feed público separado. Não coloques tokens GitHub dentro do APK.
