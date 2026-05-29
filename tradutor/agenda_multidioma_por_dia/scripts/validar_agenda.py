#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Finalidade:
Validar a estrutura dos arquivos diários e dos arquivos finais da agenda.

Uso:
  python scripts/validar_agenda.py
"""

import json
from pathlib import Path
from datetime import date

CAMPOS = ["data", "versiculo", "referencia", "contexto", "significado", "mensagem"]

def validar_item(item, origem):
    faltando = [c for c in CAMPOS if c not in item]
    if faltando:
        raise ValueError(f"{origem}: campos ausentes: {faltando}")

def main():
    root = Path(__file__).resolve().parents[1]
    problemas = []

    for folder in ["01_dias_pt", "02_traducao_en", "03_traducao_es"]:
        path = root / folder
        files = sorted(path.glob("*.json"))
        if len(files) != 365:
            problemas.append(f"{folder}: esperado 365 arquivos, encontrado {len(files)}.")

        for file in files:
            try:
                date.fromisoformat(file.stem)
                obj = json.loads(file.read_text(encoding="utf-8"))
                if folder == "01_dias_pt":
                    validar_item(obj, str(file))
                else:
                    if "traducao" not in obj or "original_pt" not in obj:
                        raise ValueError(f"{file}: esperado objeto com 'original_pt' e 'traducao'.")
                    validar_item(obj["traducao"], str(file) + " > traducao")
                    validar_item(obj["original_pt"], str(file) + " > original_pt")
            except Exception as exc:
                problemas.append(str(exc))

    final_pt = root / "04_saida_final" / "agenda_pt.json"
    if final_pt.exists():
        arr = json.loads(final_pt.read_text(encoding="utf-8"))
        if len(arr) != 365:
            problemas.append(f"agenda_pt.json: esperado 365 registros, encontrado {len(arr)}.")
        for i, item in enumerate(arr):
            try:
                validar_item(item, f"agenda_pt.json[{i}]")
            except Exception as exc:
                problemas.append(str(exc))
    else:
        problemas.append("agenda_pt.json final não encontrado.")

    if problemas:
        print("PROBLEMAS ENCONTRADOS:")
        for p in problemas[:100]:
            print(" -", p)
        if len(problemas) > 100:
            print(f" ... e mais {len(problemas)-100} problema(s).")
        raise SystemExit(1)

    print("OK: estrutura validada com sucesso.")

if __name__ == "__main__":
    main()
