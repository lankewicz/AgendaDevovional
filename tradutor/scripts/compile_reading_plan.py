import os
import json
import zipfile

ROOT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
devocionais_dir = os.path.join(ROOT_DIR, "devocionais")

plano = {}

for root, dirs, files in os.walk(devocionais_dir):
    for file in sorted(files):
        if file.startswith("dia_") and file.endswith(".json"):
            filepath = os.path.join(root, file)
            try:
                with open(filepath, "r", encoding="utf-8") as f:
                    data = json.load(f)
                dia_id = int(data.get("dia", 1))
                plano[dia_id] = {
                    "leitura_referencia": data.get("leitura_referencia", {}),
                    "leitura": data.get("leitura", [])
                }
            except Exception as e:
                print(f"Error reading {file}: {e}")
        elif file.endswith(".zip"):
            filepath = os.path.join(root, file)
            try:
                with zipfile.ZipFile(filepath, 'r') as z:
                    for name in sorted(z.namelist()):
                        base_name = os.path.basename(name)
                        if base_name.startswith("dia_") and name.endswith(".json"):
                            with z.open(name) as f:
                                data = json.loads(f.read().decode('utf-8'))
                            dia_id = int(data.get("dia", 1))
                            plano[dia_id] = {
                                "leitura_referencia": data.get("leitura_referencia", {}),
                                "leitura": data.get("leitura", [])
                            }
            except Exception as e:
                print(f"Error reading zip {file}: {e}")

# Save to dados/plano_leitura.json
output_path = os.path.join(os.path.dirname(ROOT_DIR), "dados", "plano_leitura.json")
os.makedirs(os.path.dirname(output_path), exist_ok=True)
with open(output_path, "w", encoding="utf-8") as f:
    json.dump(plano, f, ensure_ascii=False, indent=2)

print(f"Compiled reading plan for {len(plano)} days to {output_path}")
