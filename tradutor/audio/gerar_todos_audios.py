#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Finalidade:
Batch generate all audio tracks (devocional, oração, versículo, Bíblia por capítulo)
for all languages (pt, en, es) and all available voices, updating the corresponding devotional JSON files.
Supports partition month folders like '2701'.
Uses asyncio concurrent workers with semaphores and a real-time multi-slot TUI dashboard.
"""

import sys
import os
import json
import asyncio
import argparse
import shutil
import time

# Enable virtual terminal processing on Windows for ANSI colors and configure UTF-8 encoding
if os.name == 'nt':
    os.system('')
if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8')

# Add tradutor/audio directory to path to import gerar_audios
sys.path.append(os.path.dirname(os.path.abspath(__file__)))
from gerar_audios import text_to_speech_m4a

# Define Narrators/Voices (4 voices per language: 2 male, 2 female)
VOICES = {
    "pt": ["pt-BR-FranciscaNeural", "pt-BR-ThalitaNeural", "pt-BR-AntonioNeural", "pt-PT-DuarteNeural"],
    "en": ["en-US-AriaNeural", "en-US-JennyNeural", "en-US-GuyNeural", "en-US-BrianNeural"],
    "es": ["es-ES-ElviraNeural", "es-MX-DaliaNeural", "es-ES-AlvaroNeural", "es-MX-JorgeNeural"]
}

# BOOK ID to Index mapping for 66 Protestant books
BOOK_ID_TO_INDEX = {
    "GEN": 0, "EXO": 1, "LEV": 2, "NUM": 3, "DEU": 4, "JOS": 5, "JDG": 6, "RUT": 7, "1SA": 8, "2SA": 9,
    "1KI": 10, "2KI": 11, "1CH": 12, "2CH": 13, "EZR": 14, "NEH": 15, "EST": 16, "JOB": 17, "PSA": 18, "PRO": 19,
    "ECC": 20, "SNG": 21, "SOL": 21, "ISA": 22, "JER": 23, "LAM": 24, "EZK": 25, "DAN": 26, "HOS": 27, "JOL": 28,
    "AMO": 29, "OBD": 30, "JON": 31, "MIC": 32, "NAM": 33, "HAB": 34, "ZEP": 35, "HAG": 36, "ZEC": 37, "MAL": 38,
    "MAT": 39, "MRK": 40, "LUK": 41, "JHN": 42, "ACT": 43, "ROM": 44, "1CO": 45, "2CO": 46, "GAL": 47, "EPH": 48,
    "PHP": 49, "COL": 50, "1TH": 51, "2TH": 52, "1TI": 53, "2TI": 54, "TIT": 55, "PHM": 56, "HEB": 57, "JAS": 58,
    "1PE": 59, "2PE": 60, "1JN": 61, "2JN": 62, "3JN": 63, "JUD": 64, "REV": 65
}

ROOT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BIBLIA_DIR = os.path.join(ROOT_DIR, "biblia")
BIBLES = {}

def load_bibles():
    import zipfile
    for lang, filename in [("pt", "arc.json"), ("en", "web.json"), ("es", "rv1960.json")]:
        path = os.path.join(BIBLIA_DIR, filename)
        if os.path.exists(path):
            with open(path, "r", encoding="utf-8-sig") as f:
                BIBLES[lang] = json.load(f)
        else:
            zip_path = path.replace(".json", ".zip")
            if os.path.exists(zip_path):
                try:
                    with zipfile.ZipFile(zip_path, 'r') as z:
                        json_name = filename
                        if json_name not in z.namelist():
                            json_files = [n for n in z.namelist() if n.endswith('.json')]
                            if json_files:
                                json_name = json_files[0]
                        with z.open(json_name) as f:
                            BIBLES[lang] = json.loads(f.read().decode("utf-8-sig"))
                except Exception as e:
                    print(f"Error loading zip bible {zip_path}: {e}")

def get_bible_text(lang, book_id, chapter_start, verse_start=None, chapter_end=None, verse_end=None):
    if lang not in BIBLES:
        return ""
    
    book_idx = BOOK_ID_TO_INDEX.get(book_id.upper())
    if book_idx is None or book_idx >= len(BIBLES[lang]):
        return ""
    
    book = BIBLES[lang][book_idx]
    chapters = book["chapters"]
    
    text_parts = []
    if chapter_end is None:
        chapter_end = chapter_start
        
    for ch_num in range(chapter_start, chapter_end + 1):
        if ch_num <= 0 or ch_num > len(chapters):
            continue
        chapter_verses = chapters[ch_num - 1]
        
        v_start = 1
        if ch_num == chapter_start and verse_start is not None:
            v_start = verse_start
            
        v_end = len(chapter_verses)
        if ch_num == chapter_end and verse_end is not None:
            v_end = verse_end
            
        for v_num in range(v_start, v_end + 1):
            if v_num <= 0 or v_num > len(chapter_verses):
                continue
            text_parts.append(chapter_verses[v_num - 1])
            
    return " ".join(text_parts)

# Default speed/rate selector
def get_default_rate(voice):
    if voice == "pt-BR-AntonioNeural":
        return "-10%"
    return "+0%"

def format_time(seconds):
    if seconds is None or seconds < 0:
        return "--:--"
    hrs = int(seconds // 3600)
    mins = int((seconds % 3600) // 60)
    secs = int(seconds % 60)
    if hrs > 0:
        return f"{hrs:02d}:{mins:02d}:{secs:02d}"
    return f"{mins:02d}:{secs:02d}"

def draw_tui(state, status="Gerando"):
    current = state["current"]
    total = state["total"]
    start_time = state["start_time"]
    
    # Get dynamic terminal width
    columns, _ = shutil.get_terminal_size((80, 20))
    sep_line = "=" * (columns - 1)
    dash_line = "-" * (columns - 1)
    
    percent = int((current / total) * 100) if total > 0 else 0
    
    # Calculate times
    elapsed = time.time() - start_time
    if current > 0:
        avg_time = elapsed / current
        remaining = total - current
        eta = avg_time * remaining
    else:
        eta = -1

    elapsed_str = format_time(elapsed)
    eta_str = format_time(eta) if eta >= 0 else "--:--"

    # Calculate space needed for labels around progress bar
    bar_length = max(20, columns - 50)
    
    filled_length = int(bar_length * current // total) if total > 0 else 0
    bar = "█" * filled_length + "░" * (bar_length - filled_length)
    
    # Cursor to top-left home (without flickering the console)
    sys.stdout.write("\033[H")
    
    # Header
    sys.stdout.write("\033[36m" + sep_line + "\033[K\n")
    sys.stdout.write(f"{'BATALHÃO DE GERAÇÃO DE ÁUDIOS DEVOCIONAIS (Painel TUI)':^{columns-1}}\033[K\n")
    sys.stdout.write(sep_line + "\033[0m\033[K\n\n")
    
    # General Info
    sys.stdout.write(f" Status:        \033[33m{status:<40}\033[0m\033[K\n")
    sys.stdout.write(f" Tempo:         \033[34m[Decorrido: {elapsed_str}] | [Restante Estimado (ETA): {eta_str}]\033[0m\033[K\n")
    sys.stdout.write(f" Progresso:     [{bar}] {percent}% ({current}/{total} arquivos)\033[K\n\n")
    
    # Categorize slots
    criando_items = []
    convertendo_items = []
    salvando_items = []
    
    for slot in state["slots"]:
        if slot != "Vazio":
            st = slot["status"]
            if "Criando" in st or "Iniciando" in st:
                criando_items.append(slot)
            elif "Convertendo" in st:
                convertendo_items.append(slot)
            elif "Salvando" in st:
                salvando_items.append(slot)

    # 1. CRIANDO AREA
    sys.stdout.write("\033[1;33m🎙️  PASSO 1: CRIANDO ÁUDIO (Edge-TTS)\033[0m\033[K\n")
    sys.stdout.write(dash_line + "\033[K\n")
    if criando_items:
        for item in criando_items:
            voice_col = f"{item['voice']:<25}"[:25]
            fn = item['file']
            filename_width = max(20, columns - 35)
            fmt_filename = fn[:filename_width]
            sys.stdout.write(f"  \033[93m●\033[0m \033[32m{voice_col}\033[0m -> {fmt_filename:<{filename_width}}\033[K\n")
    else:
        sys.stdout.write("  \033[90mNenhum áudio sendo gerado no momento...\033[0m\033[K\n")
    sys.stdout.write("\033[K\n")

    # 2. CONVERTENDO AREA
    sys.stdout.write("\033[1;35m⚙️  PASSO 2: CONVERTENDO / OTIMIZANDO (FFmpeg - AAC 32k)\033[0m\033[K\n")
    sys.stdout.write(dash_line + "\033[K\n")
    if convertendo_items:
        for item in convertendo_items:
            voice_col = f"{item['voice']:<25}"[:25]
            fn = item['file']
            filename_width = max(20, columns - 35)
            fmt_filename = fn[:filename_width]
            sys.stdout.write(f"  \033[95m●\033[0m \033[32m{voice_col}\033[0m -> {fmt_filename:<{filename_width}}\033[K\n")
    else:
        sys.stdout.write("  \033[90mNenhuma conversão ocorrendo no momento...\033[0m\033[K\n")
    sys.stdout.write("\033[K\n")

    # 3. SALVANDO AREA
    sys.stdout.write("\033[1;36m💾  PASSO 3: SALVANDO / RECENTEMENTE CONCLUÍDOS\033[0m\033[K\n")
    sys.stdout.write(dash_line + "\033[K\n")
    
    # Active saving items first
    displayed_saving = 0
    if salvando_items:
        for item in salvando_items:
            voice_col = f"{item['voice']:<25}"[:25]
            fn = item['file']
            filename_width = max(20, columns - 45)
            fmt_filename = fn[:filename_width]
            sys.stdout.write(f"  \033[96m●\033[0m \033[32m{voice_col}\033[0m -> {fmt_filename:<{filename_width}} \033[33m(Salvando...)\033[0m\033[K\n")
            displayed_saving += 1
            
    # Then completed items from history
    recent = state.get("recent_completed", [])
    max_completed_to_show = max(1, 5 - displayed_saving)
    shown = 0
    for item in reversed(recent):
        if shown >= max_completed_to_show:
            break
        voice_col = f"{item['voice']:<25}"[:25]
        fn = item['file']
        filename_width = max(20, columns - 45)
        fmt_filename = fn[:filename_width]
        sys.stdout.write(f"  \033[92m✔\033[0m \033[32m{voice_col}\033[0m -> {fmt_filename:<{filename_width}} \033[90m(Concluído)\033[0m\033[K\n")
        shown += 1
        
    if not salvando_items and not recent:
        sys.stdout.write("  \033[90mAguardando finalização do primeiro lote...\033[0m\033[K\n")
        
    sys.stdout.write("\033[K\n")
    sys.stdout.write("\033[36m" + sep_line + "\033[0m\033[K\n")
    sys.stdout.write("\033[J")
    sys.stdout.flush()

async def run_generation_job(sem, job, state):
    async with sem:
        # Find first empty slot ONLY after we acquire the semaphore!
        slot_idx = state["slots"].index("Vazio")
        
        state["slots"][slot_idx] = {
            "voice": job["voice"],
            "file": job["output_filename"],
            "status": "Iniciando"
        }
        draw_tui(state)
        
        async def status_callback(step_name):
            state["slots"][slot_idx] = {
                "voice": job["voice"],
                "file": job["output_filename"],
                "status": step_name
            }
            draw_tui(state)
            
        await text_to_speech_m4a(
            job["text"], job["lang"], job["output_path"], 
            job["voice"], job["rate"], on_status_change=status_callback
        )
        
        state["slots"][slot_idx] = "Vazio"
        
    state["current"] += 1
    
    # Add to recent completed list (keep last 5)
    state["recent_completed"].append({
        "voice": job["voice"],
        "file": job["output_filename"],
        "status": "Finalizado"
    })
    if len(state["recent_completed"]) > 5:
        state["recent_completed"].pop(0)
        
    draw_tui(state)

async def process_day(dev_path, state, sem, overwrite=False):
    with open(dev_path, "r", encoding="utf-8") as f:
        dev_json = json.load(f)

    dia_id = dev_json.get("dia")
    if not dia_id:
        filename = os.path.basename(dev_path)
        try:
            dia_id = int(filename.split("_")[1].split(".")[0])
        except Exception:
            dia_id = 1
            
    if "audio" not in dev_json:
        dev_json["audio"] = {}

    output_dir = os.path.join(ROOT_DIR, "audio", "output")
    os.makedirs(output_dir, exist_ok=True)

    langs = ["pt", "en", "es"]
    audio_types = ["devocional", "versiculo_central", "oracao", "biblia"]
    
    jobs = []

    for lang in langs:
        if lang not in dev_json["audio"]:
            dev_json["audio"][lang] = {}

        for voice in VOICES[lang]:
            if voice not in dev_json["audio"][lang]:
                dev_json["audio"][lang][voice] = {}

            rate = get_default_rate(voice)

            for audio_type in audio_types:
                if audio_type == "biblia":
                    leitura = dev_json.get("leitura", [])
                    if "biblia" not in dev_json["audio"][lang][voice]:
                        dev_json["audio"][lang][voice]["biblia"] = {}

                    for item in leitura:
                        book_id = item.get("book_id", "")
                        ch_start = item.get("chapter_start", 1)
                        ch_end = item.get("chapter_end", 1)

                        for chapter in range(ch_start, ch_end + 1):
                            text = get_bible_text(lang, book_id, chapter)
                            if not text or not text.strip():
                                continue

                            output_rel_dir = os.path.join("biblia", book_id, lang, voice)
                            output_filename = os.path.join(output_rel_dir, f"{chapter:03d}.m4a")
                            output_path_m4a = os.path.join(output_dir, output_filename)

                            # Populate path key immediately
                            dev_json["audio"][lang][voice]["biblia"][f"{book_id}_{chapter}"] = f"audio/biblia/{book_id}/{lang}/{voice}/{chapter:03d}.m4a"
                            
                            # Check if file exists and resume is enabled
                            if not overwrite and os.path.exists(output_path_m4a) and os.path.getsize(output_path_m4a) > 0:
                                state["current"] += 1
                                continue

                            jobs.append({
                                "text": text,
                                "lang": lang,
                                "output_path": output_path_m4a,
                                "voice": voice,
                                "rate": rate,
                                "output_filename": output_filename
                            })
                else:
                    text = ""
                    if audio_type == "devocional":
                        text = dev_json.get("conteudo", {}).get(lang, {}).get("contexto", "")
                    elif audio_type == "oracao":
                        text = dev_json.get("conteudo", {}).get(lang, {}).get("oracao", "")
                    elif audio_type == "versiculo_central":
                        vc = dev_json.get("versiculo_central", {})
                        book_id = vc.get("book_id", "")
                        chapter = vc.get("chapter", 1)
                        v_start = vc.get("verse_start", 1)
                        v_end = vc.get("verse_end", 1)
                        text = get_bible_text(lang, book_id, chapter, v_start, chapter, v_end)

                    if not text or not text.strip():
                        continue

                    output_rel_dir = os.path.join("dia", f"{dia_id:03d}", lang, voice)
                    output_filename = os.path.join(output_rel_dir, f"{audio_type}.m4a")
                    output_path_m4a = os.path.join(output_dir, output_filename)

                    # Populate path key immediately
                    dev_json["audio"][lang][voice][audio_type] = f"audio/dia/{dia_id:03d}/{lang}/{voice}/{audio_type}.m4a"
                    
                    # Check if file exists and resume is enabled
                    if not overwrite and os.path.exists(output_path_m4a) and os.path.getsize(output_path_m4a) > 0:
                        state["current"] += 1
                        continue

                    jobs.append({
                        "text": text,
                        "lang": lang,
                        "output_path": output_path_m4a,
                        "voice": voice,
                        "rate": rate,
                        "output_filename": output_filename
                    })

    # Run all gathered jobs for the day concurrently using Semaphore
    if jobs:
        tasks = [run_generation_job(sem, job, state) for job in jobs]
        await asyncio.gather(*tasks)

    # Save the day's updated JSON once after all day's files are generated
    with open(dev_path, "w", encoding="utf-8") as f:
        json.dump(dev_json, f, ensure_ascii=False, indent=2)

def calculate_total_tasks(files_to_process):
    total = 0
    langs = ["pt", "en", "es"]
    audio_types = ["devocional", "versiculo_central", "oracao", "biblia"]

    for file_path in files_to_process:
        try:
            with open(file_path, "r", encoding="utf-8") as f:
                dev_json = json.load(f)
        except Exception:
            continue

        for lang in langs:
            for voice in VOICES[lang]:
                for audio_type in audio_types:
                    if audio_type == "biblia":
                        leitura = dev_json.get("leitura", [])
                        for item in leitura:
                            ch_start = item.get("chapter_start", 1)
                            ch_end = item.get("chapter_end", 1)
                            total += (ch_end - ch_start + 1)
                    else:
                        text = ""
                        if audio_type == "devocional":
                            text = dev_json.get("conteudo", {}).get(lang, {}).get("contexto", "")
                        elif audio_type == "oracao":
                            text = dev_json.get("conteudo", {}).get(lang, {}).get("oracao", "")
                        elif audio_type == "versiculo_central":
                            vc = dev_json.get("versiculo_central", {})
                            book_id = vc.get("book_id", "")
                            chapter = vc.get("chapter", 1)
                            v_start = vc.get("verse_start", 1)
                            v_end = vc.get("verse_end", 1)
                            text = get_bible_text(lang, book_id, chapter, v_start, chapter, v_end)
                        if text and text.strip():
                            total += 1
    return total

async def main():
    parser = argparse.ArgumentParser(description="Batch generate audio files for all devocionais.")
    parser.add_argument("--folder", type=str, default=None, help="Optional month folder (e.g. 2701, janeiro).")
    parser.add_argument("--dia", type=int, default=None, help="Optional specific day ID (e.g. 1).")
    parser.add_argument("--concurrency", type=int, default=30, help="Maximum concurrent audio generation tasks (default: 30).")
    parser.add_argument("--overwrite", action="store_true", help="Force overwrite existing audio files instead of skipping them.")
    args = parser.parse_args()

    load_bibles()

    devocionais_dir = os.path.join(ROOT_DIR, "devocionais")
    if args.folder:
        search_dir = os.path.join(devocionais_dir, args.folder)
    else:
        search_dir = devocionais_dir

    if not os.path.exists(search_dir):
        print(f"Error: Target directory does not exist: {search_dir}")
        return

    def ensure_decompressed(zip_path, jsons_dir):
        import zipfile
        os.makedirs(jsons_dir, exist_ok=True)
        with zipfile.ZipFile(zip_path, 'r') as z:
            for name in z.namelist():
                base_name = os.path.basename(name)
                if base_name.startswith("dia_") and base_name.endswith(".json"):
                    target_path = os.path.join(jsons_dir, base_name)
                    if not os.path.exists(target_path):
                        with z.open(name) as source, open(target_path, 'wb') as target:
                            shutil.copyfileobj(source, target)
                        print(f"Extracted {base_name} from {os.path.basename(zip_path)}")

    devocionais_dir = os.path.join(ROOT_DIR, "devocionais")
    jsons_dir = os.path.join(devocionais_dir, "JSONs")
    os.makedirs(jsons_dir, exist_ok=True)

    import zipfile

    if args.dia is not None:
        expected_filename = f"dia_{args.dia:03d}.json"
        json_path = os.path.join(jsons_dir, expected_filename)
        if not os.path.exists(json_path):
            found_zip = False
            for file in os.listdir(devocionais_dir):
                if file.endswith(".zip"):
                    zip_path = os.path.join(devocionais_dir, file)
                    try:
                        with zipfile.ZipFile(zip_path, 'r') as z:
                            for name in z.namelist():
                                if os.path.basename(name) == expected_filename:
                                    ensure_decompressed(zip_path, jsons_dir)
                                    found_zip = True
                                    break
                    except Exception as e:
                        print(f"Error scanning zip {file}: {e}")
                    if found_zip:
                        break
            if not found_zip:
                raw_path = os.path.join(devocionais_dir, expected_filename)
                if os.path.exists(raw_path):
                    shutil.copy2(raw_path, json_path)
    else:
        for file in os.listdir(devocionais_dir):
            if file.endswith(".zip"):
                zip_path = os.path.join(devocionais_dir, file)
                try:
                    with zipfile.ZipFile(zip_path, 'r') as z:
                        needs_decompress = False
                        for name in z.namelist():
                            base = os.path.basename(name)
                            if base.startswith("dia_") and base.endswith(".json"):
                                if not os.path.exists(os.path.join(jsons_dir, base)):
                                    needs_decompress = True
                                    break
                        if needs_decompress:
                            ensure_decompressed(zip_path, jsons_dir)
                except Exception as e:
                    print(f"Error checking zip {file}: {e}")

        for file in os.listdir(devocionais_dir):
            if file.startswith("dia_") and file.endswith(".json"):
                dest_path = os.path.join(jsons_dir, file)
                if not os.path.exists(dest_path):
                    shutil.copy2(os.path.join(devocionais_dir, file), dest_path)

    files_to_process = []
    if args.dia is not None:
        expected_filename = f"dia_{args.dia:03d}.json"
        target_path = os.path.join(jsons_dir, expected_filename)
        if os.path.exists(target_path):
            files_to_process.append(target_path)
    else:
        for file in sorted(os.listdir(jsons_dir)):
            if file.startswith("dia_") and file.endswith(".json"):
                files_to_process.append(os.path.join(jsons_dir, file))

    if not files_to_process:
        print("No matching devotional files found.")
        return

    # Clear console and prepare TUI screen
    os.system("cls" if os.name == "nt" else "clear")
    sys.stdout.write("\n" * (25 + args.concurrency)) # reserve lines for header + slots
    sys.stdout.flush()

    total_tasks = calculate_total_tasks(files_to_process)
    
    state = {
        "current": 0,
        "total": total_tasks,
        "start_time": time.time(),
        "slots": ["Vazio"] * args.concurrency,
        "recent_completed": []
    }

    # Initialize semaphore to limit concurrent requests (e.g., 10 parallel tasks max)
    sem = asyncio.Semaphore(args.concurrency)

    draw_tui(state, status="Iniciando...")

    for file_path in sorted(files_to_process):
        await process_day(file_path, state, sem, overwrite=args.overwrite)

    draw_tui(state, status="Finalizado")
    print("\nProcesso concluído com sucesso!")

if __name__ == "__main__":
    asyncio.run(main())
