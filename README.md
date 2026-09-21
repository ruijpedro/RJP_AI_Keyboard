# RJP AI Keyboard V1.1

## Novo: dicionários com atualização automática

Os seis dicionários (PT-PT, EN, IT, ES, FR e DE) podem agora ser atualizados sem reinstalar o APK. O GitHub mantém um `dictionary-feed` versionado e a app verifica novas versões em segundo plano. Cada download é validado por HTTPS + SHA-256 e só depois substitui o ficheiro anterior. O corretor durante a escrita permanece totalmente local e responsivo.

Consulta `AUTO_DICTIONARY_UPDATES.md` para o funcionamento completo.

---

Teclado Android nativo (IME) com foco em correção rápida e Português de Portugal.


## V1.1.1 — estabilidade e memória

A V1.1.1 corrige um problema de memória observado em Android real quando o workflow incorporava dezenas de milhares de palavras Hunspell diretamente no APK. O APK passa a incluir apenas os dicionários base e o motor recebe as versões completas através do `dictionary-feed`. O índice fuzzy foi redesenhado para manter baixo consumo de RAM e continuar a aceitar correções até distância 2.

Depois de atualizar o código, execute primeiro **Update Runtime Dictionaries** (opcional, para publicar o feed completo) e depois **Build Android APK**.

## Incluído

- Português (Portugal), Inglês, Italiano, Espanhol, Francês e Alemão.
- Sem emojis e sem painel de emojis.
- Layouts por idioma: QWERTY, AZERTY francês e QWERTZ alemão.
- Autocorreção local, sem esperar por Internet.
- Sugestões por prefixo, distância Damerau-Levenshtein e frequência.
- Pequeno modelo contextual de bigramas (palavra anterior).
- Dicionário pessoal opcional.
- Modo privado: não aprende palavras.
- Campos de palavra-passe/PIN não são analisados nem aprendidos.
- Dicionários TSV substituíveis em `app/src/main/assets/dictionaries/`.
- No GitHub Actions, tentativa automática de enriquecer os 6 dicionários com raízes Hunspell abertas; se a rede/pacote falhar, o build usa as listas locais incluídas.
- Workflow GitHub Actions que gera o APK debug automaticamente.

## Compilar no GitHub

1. Crie um repositório vazio.
2. Extraia este ZIP e envie todo o conteúdo para a raiz do repositório.
3. Abra **Actions > Build Android APK > Run workflow**.
4. No fim, descarregue o artefacto `RJP-AI-Keyboard-debug`.
5. Instale `app-debug.apk` no Android.
6. Abra a app **RJP AI Keyboard** > **Ativar teclado** > ative-o nas definições Android.
7. Use **Selecionar RJP AI Keyboard** para o escolher.

## Motor de correção

O motor não percorre todo o dicionário em cada tecla. Cada idioma constrói:

- índice de palavras exatas;
- índice de prefixos para previsão;
- índice de eliminações (SymSpell-like) para erros de 1–2 caracteres;
- ranking por frequência;
- boost de contexto por bigramas.

Apenas o idioma ativo é carregado antecipadamente. Os restantes são carregados quando o utilizador os seleciona.

## Aumentar os dicionários

Formato:

```text
palavra<TAB>frequência
```

Quanto maior a frequência, maior a prioridade da palavra. Pode substituir os TSV por listas maiores. Existe também `tools/import_hunspell.py` para converter ficheiros `.dic` Hunspell em TSV.

Para dicionários Hunspell completos, pode usar fontes abertas como o projeto `wooorm/dictionaries` / LibreOffice, respeitando a licença individual de cada dicionário. Os ficheiros do presente projeto são listas iniciais próprias e não copiam esses dicionários.

## IA

A V1 já tem correção contextual local e aprendizagem pessoal, mantendo o teclado rápido e privado. O botão **AI** força a atualização da correção inteligente da palavra atual. A integração futura com um modelo generativo deve ser feita através de um backend próprio e nunca com uma chave secreta gravada no APK.

## Privacidade

Por defeito, o **Modo privado** está ativo. Nenhuma palavra é enviada para servidores por este projeto. Campos de passwords/PIN são excluídos da análise.