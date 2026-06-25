import os
import json

ROOT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
AUDIO_OUTPUT_DIR = os.path.join(ROOT_DIR, "audio", "output")
DADOS_DIR = os.path.join(os.path.dirname(ROOT_DIR), "dados")
MANIFEST_PATH = os.path.join(DADOS_DIR, "audio_manifest.json")

def generate_manifest():
    if not os.path.exists(AUDIO_OUTPUT_DIR):
        print(f"Error: Audio output directory not found at {AUDIO_OUTPUT_DIR}")
        return

    manifest = {
        "dia": {},
        "biblia": {}
    }

    # Walk through audio/output
    for root, dirs, files in os.walk(AUDIO_OUTPUT_DIR):
        for file in files:
            if ".temp" in file or "temp" in file.lower():
                continue
            if not (file.endswith(".m4a") or file.endswith(".mp3")):
                continue
                
            # Get path relative to audio/output
            rel_path = os.path.relpath(os.path.join(root, file), AUDIO_OUTPUT_DIR)
            # Normalize to use forward slashes
            normalized_rel_path = rel_path.replace("\\", "/")
            parts = normalized_rel_path.split("/")

            try:
                full_path = os.path.join(AUDIO_OUTPUT_DIR, rel_path)
                file_size = os.path.getsize(full_path)
                file_info = {
                    "path": normalized_rel_path,
                    "size": file_size
                }

                if parts[0] == "dia" and len(parts) >= 5:
                    # Format: dia / {dia_id} / {lang} / {voice} / {type}.m4a
                    dia_id = parts[1]
                    lang = parts[2]
                    voice = parts[3]
                    audio_type = os.path.splitext(parts[4])[0]

                    if dia_id not in manifest["dia"]:
                        manifest["dia"][dia_id] = {}
                    if lang not in manifest["dia"][dia_id]:
                        manifest["dia"][dia_id][lang] = {}
                    if voice not in manifest["dia"][dia_id][lang]:
                        manifest["dia"][dia_id][lang][voice] = {}

                    manifest["dia"][dia_id][lang][voice][audio_type] = file_info

                elif parts[0] == "biblia" and len(parts) >= 5:
                    # Format: biblia / {book_id} / {lang} / {voice} / {chapter}.m4a
                    book_id = parts[1]
                    lang = parts[2]
                    voice = parts[3]
                    chapter_file = parts[4]
                    chapter = str(int(os.path.splitext(chapter_file)[0]))

                    if book_id not in manifest["biblia"]:
                        manifest["biblia"][book_id] = {}
                    if lang not in manifest["biblia"][book_id]:
                        manifest["biblia"][book_id][lang] = {}
                    if voice not in manifest["biblia"][book_id][lang]:
                        manifest["biblia"][book_id][lang][voice] = {}

                    manifest["biblia"][book_id][lang][voice][chapter] = file_info
            except Exception as e:
                print(f"Skipping invalid audio file path {normalized_rel_path}: {e}")

    os.makedirs(DADOS_DIR, exist_ok=True)
    with open(MANIFEST_PATH, "w", encoding="utf-8") as f:
        json.dump(manifest, f, ensure_ascii=False, indent=2)

    total_dia = sum(len(v) for d in manifest["dia"].values() for l in d.values() for v in l.values())
    total_biblia = sum(len(v) for b in manifest["biblia"].values() for l in b.values() for v in l.values())
    print(f"Manifest created successfully at {MANIFEST_PATH}")
    print(f"Total daily audios indexed: {total_dia}")
    print(f"Total Bible chapter audios indexed: {total_biblia}")

if __name__ == "__main__":
    generate_manifest()
