# Dicionários de terceiros

O projeto inclui dicionários iniciais próprios em TSV para funcionar offline desde a primeira instalação.

Os workflows do GitHub podem enriquecer e atualizar os dicionários a partir de pacotes Hunspell abertos:

- `dictionary-pt-pt`
- `dictionary-en-gb`
- `dictionary-it`
- `dictionary-es`
- `dictionary-fr`
- `dictionary-de`

O script `tools/enrich_dictionaries.sh` tenta obter esses pacotes e preserva os avisos de licença encontrados. O `dictionary-feed` publicado inclui também a pasta `licenses` quando esses ficheiros existem.

O publicador tem uma proteção anti-regressão: uma atualização automática nunca substitui um dicionário do feed por uma versão com menos entradas, evitando que uma falha temporária de uma fonte reduza o vocabulário distribuído.
