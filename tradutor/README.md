# Agenda Multidioma por Dia

Este pacote foi gerado a partir do arquivo original `agenda(3).json`.

## Estrutura

```text
00_original/
  agenda_pt_original.json

01_dias_pt/
  2026-01-01.json
  2026-01-02.json
  ...

02_traducao_en/
  2026-01-01.json
  2026-01-02.json
  ...

03_traducao_es/
  2026-01-01.json
  2026-01-02.json
  ...

04_saida_final/
  agenda_pt.json

scripts/
  consolidar_agenda.py
  validar_agenda.py

traducao_status.csv
```

## Como trabalhar

### 1. Editar dia por dia

Os arquivos em português estão em:

```text
01_dias_pt/
```

Os arquivos de tradução em inglês estão em:

```text
02_traducao_en/
```

Os arquivos de tradução em espanhol estão em:

```text
03_traducao_es/
```

Nos arquivos de inglês e espanhol, preencha apenas o bloco:

```json
"traducao": {
  "data": "...",
  "versiculo": "...",
  "referencia": "...",
  "contexto": "...",
  "significado": "...",
  "mensagem": "..."
}
```

O bloco `original_pt` fica como referência para tradução.

### 2. Validar estrutura

```bash
python scripts/validar_agenda.py
```

### 3. Consolidar português

```bash
python scripts/consolidar_agenda.py --lang pt
```

Gera:

```text
04_saida_final/agenda_pt.json
```

### 4. Consolidar inglês

Após preencher as traduções em inglês:

```bash
python scripts/consolidar_agenda.py --lang en
```

Gera:

```text
04_saida_final/agenda_en.json
```

### 5. Consolidar espanhol

Após preencher as traduções em espanhol:

```bash
python scripts/consolidar_agenda.py --lang es
```

Gera:

```text
04_saida_final/agenda_es.json
```

## Observação importante

Os arquivos `en` e `es` foram preparados como arquivos de trabalho, com o texto original em português dentro de `original_pt`.
O script final remove esse bloco e gera apenas a estrutura limpa esperada pelo aplicativo.
