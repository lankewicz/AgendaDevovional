#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Finalidade:
Consolidar os arquivos diários de uma agenda/devocional em um único JSON por idioma.

Uso:
  python scripts/consolidar_agenda.py --lang pt
  python scripts/consolidar_agenda.py --lang en
  python scripts/consolidar_agenda.py --lang es

Observação:
- Para PT, o script lê arquivos planos em 01_dias_pt.
- Para EN/ES, o script lê os arquivos de trabalho em 02_traducao_en / 03_traducao_es
  e extrai apenas o objeto "traducao".
- Por padrão, o script bloqueia consolidação se houver campos vazios em EN/ES.
  Use --allow-empty apenas para teste.
"""

import argparse
import json
import re
from pathlib import Path
from datetime import date

CAMPOS = ["data", "versiculo", "referencia", "contexto", "significado", "mensagem"]

MESES_PT = {
    "janeiro": 1, "fevereiro": 2, "março": 3, "marco": 3, "abril": 4,
    "maio": 5, "junho": 6, "julho": 7, "agosto": 8, "setembro": 9,
    "outubro": 10, "novembro": 11, "dezembro": 12
}
MESES_EN = {
    "january": 1, "february": 2, "march": 3, "april": 4, "may": 5, "june": 6,
    "july": 7, "august": 8, "september": 9, "october": 10, "november": 11, "december": 12
}
MESES_ES = {
    "enero": 1, "febrero": 2, "marzo": 3, "abril": 4, "mayo": 5, "junio": 6,
    "julio": 7, "agosto": 8, "septiembre": 9, "setiembre": 9, "octubre": 10,
    "noviembre": 11, "diciembre": 12
}

def parse_date_from_filename(path: Path) -> date:
    return date.fromisoformat(path.stem)

def load_daily(path: Path, lang: str):
    obj = json.loads(path.read_text(encoding="utf-8"))

    if lang == "pt":
        item = obj
    else:
        if "traducao" not in obj:
            raise ValueError(f"{path}: arquivo de tradução sem objeto 'traducao'.")
        item = obj["traducao"]

    missing = [c for c in CAMPOS if c not in item]
    if missing:
        raise ValueError(f"{path}: campos ausentes: {missing}")

    return {c: item[c] for c in CAMPOS}

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--lang", choices=["pt", "en", "es"], required=True)
    parser.add_argument("--allow-empty", action="store_true")
    args = parser.parse_args()

    root = Path(__file__).resolve().parents[1]
    source_dir = {
        "pt": root / "01_dias_pt",
        "en": root / "02_traducao_en",
        "es": root / "03_traducao_es",
    }[args.lang]
    output_dir = root / "04_saida_final"
    output_dir.mkdir(exist_ok=True)

    files = sorted(source_dir.glob("*.json"), key=parse_date_from_filename)
    if not files:
        raise SystemExit(f"Nenhum arquivo .json encontrado em {source_dir}")

    items = []
    errors = []
    for file in files:
        try:
            item = load_daily(file, args.lang)
            if args.lang in ("en", "es") and not args.allow_empty:
                empty = [c for c in CAMPOS if not str(item[c]).strip()]
                if empty:
                    errors.append(f"{file.name}: campos vazios: {', '.join(empty)}")
                    continue
            items.append(item)
        except Exception as exc:
            errors.append(f"{file.name}: {exc}")

    if errors:
        print("Não foi possível consolidar. Corrija os itens abaixo:")
        for err in errors[:80]:
            print(" -", err)
        if len(errors) > 80:
            print(f" ... e mais {len(errors) - 80} erro(s).")
        raise SystemExit(1)

    output = output_dir / f"agenda_{args.lang}.json"
    output.write_text(json.dumps(items, ensure_ascii=False, indent=2), encoding="utf-8")

    print(f"OK: {len(items)} registros consolidados em {output}")

if __name__ == "__main__":
    main()
