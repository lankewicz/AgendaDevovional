# Agenda Multidioma por Dia — Sistema de Tradução

Este pacote contém os scripts e a estrutura necessários para segmentar, traduzir e consolidar o banco de dados da **Agenda Devocional** (de/para Português, Inglês e Espanhol).

A principal vantagem deste sistema é trabalhar com arquivos JSON diários e individuais, o que facilita a tradução passo a passo, evita conflitos de mesclagem e permite correções pontuais sem a necessidade de editar um arquivo monolítico gigante.

---

## 📂 Estrutura de Diretórios (Tempo de Execução)

A estrutura completa abaixo é gerada dinamicamente à medida que os scripts são executados:

```text
tradutor/
├── README.md                          # Este manual de instruções
├── traducao_status.csv                # [Gerado] Planilha de controle do status de tradução por dia
│
├── 00_original/                       # [Requerido] Pasta para colocar o arquivo fonte original
│   └── agenda_pt_original.json        # Arquivo JSON monolítico em português (base para segmentação)
│
├── 01_dias_pt/                        # [Gerado] Um arquivo JSON para cada dia do ano em português
│   ├── 2026-01-01.json
│   └── ...
│
├── 02_traducao_en/                    # [Gerado] Templates e arquivos traduzidos para o inglês
│   ├── 2026-01-01.json
│   └── ...
│
├── 03_traducao_es/                    # [Gerado] Templates e arquivos traduzidos para o espanhol
│   ├── 2026-01-01.json
│   └── ...
│
├── 04_saida_final/                    # [Gerado] Pasta com os arquivos consolidados e limpos prontos para o app
│   ├── agenda_pt.json
│   ├── agenda_en.json
│   └── agenda_es.json
│
└── scripts/                           # Pasta de scripts utilitários
    ├── recreate_dias.py               # Segmenta o arquivo original em dias individuais
    ├── traduzir_agenda.py             # Script interativo de tradução automática via APIs e Deep Translator
    ├── validar_agenda.py              # Validador de integridade da estrutura dos arquivos JSON
    └── consolidar_agenda.py           # Junta os arquivos diários em arquivos monolíticos por idioma
```

> [!NOTE]
> Os diretórios numerados (`00_original` a `04_saida_final`) e o arquivo `traducao_status.csv` são omitidos do controle de versão Git para evitar poluição no histórico com milhares de arquivos JSON diários.

---

## 🛠️ Descrição dos Scripts

### 1. `recreate_dias.py`
Lê o arquivo `00_original/agenda_pt_original.json`, analisa as datas brasileiras (ex: "01 de Janeiro de 2026") e gera:
- Arquivos diários em português dentro de `01_dias_pt/` nomeados com a data ISO (`YYYY-MM-DD.json`).
- Templates de tradução vazios com status `"pendente"` em `02_traducao_en/` e `03_traducao_es/`.

### 2. `traduzir_agenda.py`
O motor principal de tradução automática. Ele é totalmente **retomável** (continua de onde parou) e processa os dias com status `"pendente"`:
- **Textos gerais** (mensagem, contexto e significado): Traduzidos usando a biblioteca `deep-translator` (Google Translate).
- **Versículos Bíblicos**: Obtidos diretamente de APIs oficiais:
  - **Inglês**: [bible-api.com](https://bible-api.com) (World English Bible - WEB).
  - **Espanhol**: [bible-api.deno.dev](https://bible-api.deno.dev) (Reina Valera 1960 - RV1960).
  - *Fallback*: Se as APIs falharem ou o livro não for mapeado, recorre à tradução automática do texto em português.
- **TUI Interativa (Painel no Terminal)**: Mostra em tempo real o progresso geral, barra de progresso do dia, delay entre requisições (para evitar bloqueios de IP) e permite pausar apertando `ESPAÇO` ou ajustar o delay usando as teclas `+` e `-`.

### 3. `validar_agenda.py`
Varre as pastas de trabalho (`01_dias_pt`, `02_traducao_en`, `03_traducao_es`) e os arquivos finais em `04_saida_final` para garantir que:
- Há exatamente 365 arquivos em cada pasta.
- Todos os JSONs possuem a estrutura correta com todas as chaves obrigatórias (`data`, `versiculo`, `referencia`, `contexto`, `significado`, `mensagem`).

### 4. `consolidar_agenda.py`
Junta as traduções diárias de volta em um único arquivo limpo na pasta `04_saida_final/`. Para inglês e espanhol, ele descarta os metadados de controle (como `status` e `original_pt`) deixando apenas a estrutura pura de dados consumida pelo aplicativo móvel.

---

## 🔄 Fluxo de Trabalho Passo a Passo

### Passo 1: Preparação
Crie a pasta `00_original` dentro de `tradutor` e coloque o arquivo da agenda em português lá com o nome `agenda_pt_original.json`.

### Passo 2: Segmentação em dias
Execute o script de segmentação para criar todos os arquivos individuais:
```bash
python scripts/recreate_dias.py
```

### Passo 3: Tradução Automática
Execute o script de tradução. Você pode limitar a quantidade de dias a traduzir por vez para testar ou rodar em lotes:

*   **Traduzir ambos os idiomas (padrão):**
    ```bash
    python scripts/traduzir_agenda.py
    ```
*   **Traduzir apenas um idioma específico:**
    ```bash
    python scripts/traduzir_agenda.py --lang en
    # ou
    python scripts/traduzir_agenda.py --lang es
    ```
*   **Limitar a execução a um número específico de dias (ex: 5 dias):**
    ```bash
    python scripts/traduzir_agenda.py --limit 5
    ```

> [!TIP]
> Durante a execução de `traduzir_agenda.py`, você pode pressionar a tecla `ESPAÇO` para pausar/retomar a tradução, ou usar `+` e `-` para aumentar/diminuir o intervalo de segurança (delay) entre as requisições às APIs de tradução.

### Passo 4: Validação Estrutural
Após concluir a tradução (ou revisão manual se necessária), certifique-se de que a estrutura está intacta:
```bash
python scripts/validar_agenda.py
```

### Passo 5: Consolidação Final
Gere os arquivos consolidados finais que serão usados no app:

```bash
# Consolida a Agenda em Português
python scripts/consolidar_agenda.py --lang pt

# Consolida a Agenda em Inglês
python scripts/consolidar_agenda.py --lang en

# Consolida a Agenda em Espanhol
python scripts/consolidar_agenda.py --lang es
```

Os arquivos finais serão gerados em `04_saida_final/` (`agenda_pt.json`, `agenda_en.json`, `agenda_es.json`).
