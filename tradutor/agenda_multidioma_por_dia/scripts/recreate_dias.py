import json
from pathlib import Path

MESES_PT = {
    "janeiro": 1, "fevereiro": 2, "março": 3, "marco": 3, "abril": 4,
    "maio": 5, "junho": 6, "julho": 7, "agosto": 8, "setembro": 9,
    "outubro": 10, "novembro": 11, "dezembro": 12
}

def recreate_files():
    root = Path(__file__).resolve().parents[1]
    original_file = root / "00_original" / "agenda_pt_original.json"
    
    if not original_file.exists():
        print(f"Error: Original file {original_file} not found.")
        return
        
    dias_pt_dir = root / "01_dias_pt"
    trad_en_dir = root / "02_traducao_en"
    trad_es_dir = root / "03_traducao_es"
    saida_dir = root / "04_saida_final"
    
    dias_pt_dir.mkdir(exist_ok=True)
    trad_en_dir.mkdir(exist_ok=True)
    trad_es_dir.mkdir(exist_ok=True)
    saida_dir.mkdir(exist_ok=True)
    
    items = json.loads(original_file.read_text(encoding="utf-8"))
    print(f"Loaded {len(items)} items from original JSON.")
    
    for item in items:
        date_str = item.get("data", "")
        # Parse date, e.g. "01 de Janeiro de 2026"
        parts = date_str.lower().split(" de ")
        if len(parts) != 3:
            print(f"Skipping invalid date: {date_str}")
            continue
        day_str, month_pt, year_str = parts
        day = int(day_str)
        month = MESES_PT.get(month_pt)
        year = int(year_str)
        
        date_iso = f"{year}-{month:02d}-{day:02d}"
        
        # 1. Portuguese file
        pt_path = dias_pt_dir / f"{date_iso}.json"
        pt_path.write_text(json.dumps(item, ensure_ascii=False, indent=2), encoding="utf-8")
        
        # 2. English translation template
        en_path = trad_en_dir / f"{date_iso}.json"
        en_template = {
            "id": date_iso,
            "idioma": "en",
            "status": "pendente",
            "original_pt": item,
            "traducao": {
                "data": "",
                "versiculo": "",
                "referencia": "",
                "contexto": "",
                "significado": "",
                "mensagem": ""
            }
        }
        en_path.write_text(json.dumps(en_template, ensure_ascii=False, indent=2), encoding="utf-8")
        
        # 3. Spanish translation template
        es_path = trad_es_dir / f"{date_iso}.json"
        es_template = {
            "id": date_iso,
            "idioma": "es",
            "status": "pendente",
            "original_pt": item,
            "traducao": {
                "data": "",
                "versiculo": "",
                "referencia": "",
                "contexto": "",
                "significado": "",
                "mensagem": ""
            }
        }
        es_path.write_text(json.dumps(es_template, ensure_ascii=False, indent=2), encoding="utf-8")
        
    print("Successfully recreated all files in 01_dias_pt, 02_traducao_en, 03_traducao_es.")

if __name__ == "__main__":
    recreate_files()
