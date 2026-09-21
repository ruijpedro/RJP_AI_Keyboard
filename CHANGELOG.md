# Changelog

## 1.1.1

- Corrige crash/OOM no arranque com dicionários Hunspell completos.
- Motor fuzzy redesenhado com índice de eliminações distância 1 limitado a 12 000 palavras e fallback por comprimento.
- Mantém correção final até distância Damerau-Levenshtein 2 sem criar milhões de chaves em memória.
- Carregamento dos dicionários protegido: uma atualização inválida nunca impede o teclado de arrancar.
- Atualização automática e JobScheduler isolados com tratamento de erros.
- O workflow de build deixa de embutir os dicionários gigantes no APK; os dicionários completos chegam pelo feed automático.
- `build.yml` legado passa a ser válido e deixa de falhar em cada push.

## 1.1.0
- Atualização automática dos seis dicionários sem reinstalar o APK.
- Feed de dicionários alojado no próprio repositório GitHub.
- Verificação em segundo plano a cada 24 horas e verificação rápida no arranque (limitada a cada 6 horas).
- Workflow semanal para atualizar o feed a partir das fontes Hunspell abertas.
- Download apenas via HTTPS, limite de tamanho, validação de formato e SHA-256.
- Troca atómica: um download inválido nunca substitui o último dicionário funcional.
- Botão "Atualizar agora" e estado da última atualização nas definições.
- O motor de correção continua 100% local durante a escrita.
- Removido o símbolo gráfico de definições do teclado; interface textual, sem emojis.

## 1.0.0
- Teclado Android IME nativo.
- PT-PT, EN, IT, ES, FR e DE.
- Autocorreção, sugestões, contexto, dicionário pessoal, modo privado e IA opcional.
