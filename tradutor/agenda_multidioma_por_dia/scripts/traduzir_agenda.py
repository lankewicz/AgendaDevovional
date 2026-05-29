#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Finalidade:
Traduzir os arquivos diários pendentes em 02_traducao_en e 03_traducao_es de PT para EN/ES.
- Mensagens, contextos e significados são traduzidos usando deep-translator (Google Translate).
- Versículos são obtidos de APIs oficiais de Bíblia:
  - EN: bible-api.com (World English Bible)
  - ES: bible-api.deno.dev (Reina Valera 1960)
- O script é totalmente retomável (apenas processa arquivos com status "pendente").
- TUI livre de cintilação (no-flicker) atualizando duas linhas de status no rodapé em tempo real
  usando códigos de controle de cursor ANSI.
- Exibe o progresso geral, contagem regressiva diária com barra de progresso própria,
  as versões utilizadas e logs do passo-a-passo detalhado.

Uso:
  python scripts/traduzir_agenda.py --limit 3
  python scripts/traduzir_agenda.py --lang en
  python scripts/traduzir_agenda.py --lang es
"""

import argparse
import json
import time
import urllib.request
import urllib.parse
import urllib.error
import ssl
import sys
import os
import textwrap
from pathlib import Path
from deep_translator import GoogleTranslator

# Tenta importar msvcrt para detecção de teclas no Windows
try:
    import msvcrt
    HAS_MSVCRT = True
except ImportError:
    HAS_MSVCRT = False

# Configura saída UTF-8 para o terminal Windows e ativa suporte a cores ANSI
sys.stdout.reconfigure(encoding='utf-8')
if os.name == 'nt':
    os.system('')

# Ignora verificação SSL (útil para APIs com certificados expirados)
ctx = ssl._create_unverified_context()

# Mapeamento de todos os livros da Bíblia (PT -> EN, ES)
BOOK_MAPPING = {
    "gênesis": {"en": "Genesis", "es": "Génesis"},
    "genesis": {"en": "Genesis", "es": "Génesis"},
    "êxodo": {"en": "Exodus", "es": "Éxodo"},
    "exodo": {"en": "Exodus", "es": "Éxodo"},
    "levítico": {"en": "Leviticus", "es": "Levítico"},
    "levitico": {"en": "Leviticus", "es": "Levítico"},
    "números": {"en": "Numbers", "es": "Números"},
    "numeros": {"en": "Numbers", "es": "Números"},
    "deuteronômio": {"en": "Deuteronomy", "es": "Deuteronomio"},
    "deuteronomio": {"en": "Deuteronomy", "es": "Deuteronomio"},
    "josué": {"en": "Joshua", "es": "Josué"},
    "josue": {"en": "Joshua", "es": "Josué"},
    "juízes": {"en": "Judges", "es": "Jueces"},
    "juizes": {"en": "Judges", "es": "Jueces"},
    "rute": {"en": "Ruth", "es": "Rut"},
    "1 samuel": {"en": "1 Samuel", "es": "1 Samuel"},
    "2 samuel": {"en": "2 Samuel", "es": "2 Samuel"},
    "1 reis": {"en": "1 Kings", "es": "1 Reyes"},
    "2 reis": {"en": "2 Kings", "es": "2 Reyes"},
    "1 crônicas": {"en": "1 Chronicles", "es": "1 Crónicas"},
    "1 cronicas": {"en": "1 Chronicles", "es": "1 Crónicas"},
    "2 crônicas": {"en": "2 Chronicles", "es": "2 Crónicas"},
    "2 cronicas": {"en": "2 Chronicles", "es": "2 Crónicas"},
    "esdras": {"en": "Ezra", "es": "Esdras"},
    "neemias": {"en": "Nehemiah", "es": "Nehemias"},
    "nehemias": {"en": "Nehemiah", "es": "Nehemías"},
    "ester": {"en": "Esther", "es": "Ester"},
    "jó": {"en": "Job", "es": "Job"},
    "jo": {"en": "Job", "es": "Job"},
    "salmos": {"en": "Psalms", "es": "Salmos"},
    "provérbios": {"en": "Proverbs", "es": "Proverbios"},
    "proverbios": {"en": "Proverbs", "es": "Proverbios"},
    "eclesiastes": {"en": "Ecclesiastes", "es": "Eclesiastes"},
    "cantares": {"en": "Song of Solomon", "es": "Cantares"},
    "cântico dos cânticos": {"en": "Song of Solomon", "es": "Cantares"},
    "isaías": {"en": "Isaiah", "es": "Isaías"},
    "isaias": {"en": "Isaiah", "es": "Isaías"},
    "jeremias": {"en": "Jeremiah", "es": "Jeremías"},
    "lamentações": {"en": "Lamentations", "es": "Lamentaciones"},
    "lamentacoes": {"en": "Lamentations", "es": "Lamentaciones"},
    "ezequiel": {"en": "Ezekiel", "es": "Ezequiel"},
    "daniel": {"en": "Daniel", "es": "Daniel"},
    "oseias": {"en": "Hosea", "es": "Oseas"},
    "oséias": {"en": "Hosea", "es": "Oseas"},
    "joel": {"en": "Joel", "es": "Joel"},
    "amós": {"en": "Amos", "es": "Amós"},
    "amos": {"en": "Amos", "es": "Amós"},
    "obadias": {"en": "Obadiah", "es": "Abdías"},
    "jonas": {"en": "Jonah", "es": "Jonás"},
    "miqueias": {"en": "Micah", "es": "Miqueas"},
    "miquéias": {"en": "Micah", "es": "Miqueas"},
    "naum": {"en": "Nahum", "es": "Naum"},
    "habacuque": {"en": "Habakkuk", "es": "Habacuc"},
    "sofonias": {"en": "Zephaniah", "es": "Sofonías"},
    "ageu": {"en": "Haggai", "es": "Hageo"},
    "zacarias": {"en": "Zechariah", "es": "Zacarías"},
    "malaquias": {"en": "Malachi", "es": "Malaquías"},
    "mateus": {"en": "Matthew", "es": "Mateo"},
    "marcos": {"en": "Mark", "es": "Marcos"},
    "lucas": {"en": "Luke", "es": "Lucas"},
    "joão": {"en": "John", "es": "Juan"},
    "joao": {"en": "John", "es": "Juan"},
    "atos": {"en": "Acts", "es": "Hechos"},
    "romanos": {"en": "Romans", "es": "Romanos"},
    "1 coríntios": {"en": "1 Corinthians", "es": "1 Corintios"},
    "1 corintios": {"en": "1 Corinthians", "es": "1 Corintios"},
    "2 coríntios": {"en": "2 Corinthians", "es": "2 Corintios"},
    "2 corintios": {"en": "2 Corinthians", "es": "2 Corintios"},
    "gálatas": {"en": "Galatians", "es": "Gálatas"},
    "galatas": {"en": "Galatians", "es": "Gálatas"},
    "efésios": {"en": "Ephesians", "es": "Efesios"},
    "efesios": {"en": "Ephesians", "es": "Efesios"},
    "filipenses": {"en": "Philippians", "es": "Filipenses"},
    "colossenses": {"en": "Colossians", "es": "Colosenses"},
    "1 tessalonicenses": {"en": "1 Thessalonians", "es": "1 Tesalonicenses"},
    "2 tessalonicenses": {"en": "2 Thessalonians", "es": "2 Tesalonicenses"},
    "1 timóteo": {"en": "1 Timothy", "es": "1 Timoteo"},
    "1 timoteo": {"en": "1 Timothy", "es": "1 Timoteo"},
    "2 timóteo": {"en": "2 Timothy", "es": "2 Timoteo"},
    "2 timoteo": {"en": "2 Timothy", "es": "2 Timoteo"},
    "tito": {"en": "Titus", "es": "Tito"},
    "filemom": {"en": "Philemon", "es": "Filemón"},
    "filemon": {"en": "Philemon", "es": "Filemón"},
    "hebreus": {"en": "Hebrews", "es": "Hebreos"},
    "tiago": {"en": "James", "es": "Santiago"},
    "1 pedro": {"en": "1 Peter", "es": "1 Pedro"},
    "2 pedro": {"en": "2 Peter", "es": "2 Pedro"},
    "1 joão": {"en": "1 John", "es": "1 Juan"},
    "1 joao": {"en": "1 John", "es": "1 Juan"},
    "2 joão": {"en": "2 John", "es": "2 Juan"},
    "2 joao": {"en": "2 John", "es": "2 Juan"},
    "3 joão": {"en": "3 John", "es": "3 Juan"},
    "3 joao": {"en": "3 John", "es": "3 Juan"},
    "judas": {"en": "Jude", "es": "Judas"},
    "apocalipse": {"en": "Revelation", "es": "Apocalipsis"}
}

MONTHS_PT_TO_EN = {
    "janeiro": "January", "fevereiro": "February", "março": "March", "abril": "April",
    "maio": "May", "junho": "June", "julho": "July", "agosto": "August",
    "setembro": "September", "outubro": "October", "novembro": "November", "dezembro": "December"
}

MONTHS_PT_TO_ES = {
    "janeiro": "enero", "fevereiro": "febrero", "março": "marzo", "abril": "abril",
    "maio": "mayo", "junho": "junio", "julho": "julio", "agosto": "agosto",
    "setembro": "septiembre", "outubro": "octubre", "novembro": "noviembre", "dezembro": "diciembre"
}

def clean_book_name(book_str):
    return book_str.strip().lower()

def parse_reference(ref_str):
    ref_str = ref_str.strip()
    rparts = ref_str.rsplit(" ", 1)
    if len(rparts) < 2:
        return None
    book_part, coords = rparts
    coords_parts = coords.split(":")
    if len(coords_parts) < 2:
        return None
    chapter, verses = coords_parts
    return book_part, chapter, verses

def translate_date(date_str, lang):
    parts = date_str.lower().split(" de ")
    if len(parts) != 3:
        return date_str
    day_str, month_pt, year_str = parts
    try:
        day = int(day_str)
    except ValueError:
        day = day_str
    if lang == "en":
        month_en = MONTHS_PT_TO_EN.get(month_pt, month_pt.capitalize())
        return f"{month_en} {day}, {year_str}"
    elif lang == "es":
        month_es = MONTHS_PT_TO_ES.get(month_pt, month_pt)
        return f"{day} de {month_es} de {year_str}"
    return date_str

def translate_reference(ref_str, lang):
    parsed = parse_reference(ref_str)
    if not parsed:
        return ref_str
    book_part, chapter, verses = parsed
    clean_book = clean_book_name(book_part)
    if clean_book in BOOK_MAPPING:
        mapped_book = BOOK_MAPPING[clean_book][lang]
        return f"{mapped_book} {chapter}:{verses}"
    return ref_str

def fetch_verse_en(book_en, chapter, verses):
    query = f"{book_en} {chapter}:{verses}"
    url = f"https://bible-api.com/{urllib.parse.quote(query)}"
    try:
        req = urllib.request.urlopen(url, context=ctx)
        data = json.loads(req.read().decode('utf-8'))
        text = data.get("text", "").strip()
        text = " ".join(text.split())
        return text
    except Exception as e:
        return None

def fetch_verse_es(book_es, chapter, verses):
    api_book = book_es.replace(" ", "-").lower()
    replacements = {"é": "e", "í": "i", "ó": "o", "ú": "u", "á": "a"}
    for char, rep in replacements.items():
        api_book = api_book.replace(char, rep)
        
    url = f"https://bible-api.deno.dev/api/read/rv1960/{urllib.parse.quote(api_book)}/{chapter}/{verses}"
    try:
        req = urllib.request.urlopen(url, context=ctx)
        data = json.loads(req.read().decode('utf-8'))
        if isinstance(data, list):
            return " ".join(v["verse"].strip() for v in data)
        elif isinstance(data, dict):
            return data.get("verse", "").strip()
        return None
    except Exception as e:
        return None

def update_status_csv(root):
    csv_path = root / "traducao_status.csv"
    en_dir = root / "02_traducao_en"
    es_dir = root / "03_traducao_es"
    
    lines = ["data_iso,pt,en,es,status_en,status_es"]
    for en_file in sorted(en_dir.glob("*.json")):
        date_iso = en_file.stem
        es_file = es_dir / en_file.name
        
        status_en = "pendente"
        if en_file.exists():
            try:
                obj_en = json.loads(en_file.read_text(encoding="utf-8"))
                status_en = obj_en.get("status", "pendente")
            except:
                pass
                
        status_es = "pendente"
        if es_file.exists():
            try:
                obj_es = json.loads(es_file.read_text(encoding="utf-8"))
                status_es = obj_es.get("status", "pendente")
            except:
                pass
        
        lines.append(f"{date_iso},01_dias_pt/{date_iso}.json,02_traducao_en/{date_iso}.json,03_traducao_es/{date_iso}.json,{status_en},{status_es}")
        
    csv_path.write_text("\n".join(lines) + "\n", encoding="utf-8")

def wrap_and_prefix(label, text, width=95):
    prefix = f"{label}: "
    indent = " " * len(prefix)
    wrapped = textwrap.wrap(text, width=width - len(prefix))
    if not wrapped:
        return f"\033[1;33m{prefix}\033[0m(vazio)"
    
    lines = []
    lines.append(f"\033[1;33m{prefix}\033[0m{wrapped[0]}")
    for line in wrapped[1:]:
        lines.append(f"{indent}{line}")
    return "\n".join(lines)

def draw_dashboard(date_iso, orig, en_tr, es_tr, state, progress_info):
    # Limpa a tela uma única vez no início de cada dia
    os.system('cls' if os.name == 'nt' else 'clear')
    
    # Cabeçalho
    print("=" * 100)
    print(f"\033[1;36mDEVOCIONAL DIÁRIO - MONITOR DE TRADUÇÃO ({date_iso})\033[0m")
    print("=" * 100)
    
    # Barra de Progresso Geral
    p_percent = progress_info["percent"]
    p_bar_len = 40
    p_filled = int(p_bar_len * p_percent / 100.0)
    p_bar = "█" * p_filled + "░" * (p_bar_len - p_filled)
    
    print(f"Progresso Geral: [{p_bar}] {p_percent:.1f}% ({progress_info['translated']}/{progress_info['total']} dias traduzidos)")
    if state.get("last_day_stats"):
        stats = state["last_day_stats"]
        print(f"Tempo no versículo anterior: \033[1;33m{stats['total']:.1f}s\033[0m (Processamento: {stats['proc']:.1f}s | Delay: {stats['delay']:.1f}s)")
    else:
        print("Tempo no versículo anterior: \033[1;30mN/A\033[0m")
    print("-" * 100)
    
    # Original PT
    print("\033[1;34m[ORIGINAL (PORTUGUÊS)]\033[0m")
    print(f"Data: {orig.get('data', '')} | Referência: {orig.get('referencia', '')}")
    print(wrap_and_prefix("Versículo", orig.get("versiculo", "")))
    print(wrap_and_prefix("Mensagem", orig.get("mensagem", "")))
    print(wrap_and_prefix("Contexto", orig.get("contexto", "")))
    print(wrap_and_prefix("Significado", orig.get("significado", "")))
    print("-" * 100)
    
    # English EN
    print("\033[1;32m[TRADUÇÃO INGLÊS (ENGLISH - Versão: WEB - World English Bible)]\033[0m")
    if en_tr:
        print(f"Data: {en_tr.get('data', '')} | Referência: {en_tr.get('referencia', '')}")
        print(wrap_and_prefix("Versículo", en_tr.get("versiculo", "")))
        print(wrap_and_prefix("Mensagem", en_tr.get("mensagem", "")))
        print(wrap_and_prefix("Contexto", en_tr.get("contexto", "")))
        print(wrap_and_prefix("Significado", en_tr.get("significado", "")))
    else:
        print("(Traduzindo...)")
    print("-" * 100)
    
    # Spanish ES
    print("\033[1;35m[TRADUÇÃO ESPANHOL (ESPAÑOL - Versão: RV1960 - Reina Valera 1960)]\033[0m")
    if es_tr:
        print(f"Data: {es_tr.get('data', '')} | Referência: {es_tr.get('referencia', '')}")
        print(wrap_and_prefix("Versículo", es_tr.get("versiculo", "")))
        print(wrap_and_prefix("Mensagem", es_tr.get("mensagem", "")))
        print(wrap_and_prefix("Contexto", es_tr.get("contexto", "")))
        print(wrap_and_prefix("Significado", es_tr.get("significado", "")))
    else:
        print("(Traduzindo...)")
    print("=" * 100)

def wait_with_interactive_keys(state, update_fn):
    state["is_waiting"] = True
    try:
        start_time = time.time()
        elapsed_before_pause = 0.0
        last_second = -1
        
        while True:
            # 1. Detecção de teclas (executada primeiro para permitir intervenções no limiar de 0s)
            if HAS_MSVCRT and msvcrt.kbhit():
                try:
                    ch = msvcrt.getch()
                    char_str = ch.decode('utf-8', errors='ignore')
                    if char_str == '+':
                        state["delay"] += 5.0
                        if state["paused"]:
                            remaining = state["delay"] - elapsed_before_pause
                        else:
                            remaining = state["delay"] - (time.time() - start_time + elapsed_before_pause)
                        update_fn(int(max(0, remaining)))
                    elif char_str == '-':
                        state["delay"] = max(0.0, state["delay"] - 5.0)
                        if state["paused"]:
                            remaining = state["delay"] - elapsed_before_pause
                        else:
                            remaining = state["delay"] - (time.time() - start_time + elapsed_before_pause)
                        update_fn(int(max(0, remaining)))
                    elif char_str == ' ':
                        state["paused"] = not state["paused"]
                        if state["paused"]:
                            elapsed_before_pause += time.time() - start_time
                        else:
                            start_time = time.time()
                        if state["paused"]:
                            remaining = state["delay"] - elapsed_before_pause
                        else:
                            remaining = state["delay"] - (time.time() - start_time + elapsed_before_pause)
                        update_fn(int(max(0, remaining)))
                except Exception:
                    pass

            # 2. Calcula tempo restante
            if state["paused"]:
                remaining = state["delay"] - elapsed_before_pause
            else:
                remaining = state["delay"] - (time.time() - start_time + elapsed_before_pause)
                
            if remaining <= 0:
                break
                
            current_second = int(max(0, remaining))
            if current_second != last_second or state["paused"]:
                update_fn(current_second)
                last_second = current_second
                if state["paused"]:
                    time.sleep(0.1)
                    
            time.sleep(0.05)
    finally:
        state["is_waiting"] = False

def process_file(file_path, lang, step_callback):
    try:
        obj = json.loads(file_path.read_text(encoding="utf-8"))
        if obj.get("status") != "pendente":
            return False, obj.get("traducao")
            
        orig = obj.get("original_pt", {})
        ref_pt = orig.get("referencia", "")
        verse_pt = orig.get("versiculo", "")
        msg_pt = orig.get("mensagem", "")
        ctx_pt = orig.get("contexto", "")
        sig_pt = orig.get("significado", "")
        date_pt = orig.get("data", "")
        
        # 1. Traduz data e referência
        step_callback(f"Formatando data e referência para {lang.upper()}...")
        date_tr = translate_date(date_pt, lang)
        ref_tr = translate_reference(ref_pt, lang)
        
        # 2. Busca versículo na API
        step_callback(f"Buscando versículo {ref_tr} na API ({lang.upper()})...")
        verse_tr = None
        parsed = parse_reference(ref_pt)
        if parsed:
            book_part, chapter, verses = parsed
            clean_book = clean_book_name(book_part)
            if clean_book in BOOK_MAPPING:
                mapped_book = BOOK_MAPPING[clean_book][lang]
                if lang == "en":
                    verse_tr = fetch_verse_en(mapped_book, chapter, verses)
                elif lang == "es":
                    verse_tr = fetch_verse_es(mapped_book, chapter, verses)
                    
        if not verse_tr:
            step_callback(f"Usando tradução automática como fallback para versículo ({lang.upper()})...")
            verse_tr = GoogleTranslator(source='pt', target=lang).translate(verse_pt)
            
        # 3. Traduz textos
        translator = GoogleTranslator(source='pt', target=lang)
        step_callback(f"Traduzindo mensagem do dia para {lang.upper()}...")
        msg_tr = translator.translate(msg_pt) if msg_pt.strip() else ""
        
        step_callback(f"Traduzindo contexto histórico para {lang.upper()}...")
        ctx_tr = translator.translate(ctx_pt) if ctx_pt.strip() else ""
        
        step_callback(f"Traduzindo significado prático para {lang.upper()}...")
        sig_tr = translator.translate(sig_pt) if sig_pt.strip() else ""
        
        # 4. Salva no JSON
        step_callback(f"Gravando arquivo diário {file_path.name}...")
        obj["status"] = "traduzido"
        obj["traducao"] = {
            "data": date_tr,
            "versiculo": verse_tr,
            "referencia": ref_tr,
            "contexto": ctx_tr,
            "significado": sig_tr,
            "mensagem": msg_tr
        }
        
        file_path.write_text(json.dumps(obj, ensure_ascii=False, indent=2), encoding="utf-8")
        return True, obj["traducao"]
    except Exception as e:
        return False, None

def get_progress_info(en_dir, es_dir):
    total = 730 # 365 dias * 2 idiomas
    translated = 0
    
    for f in en_dir.glob("*.json"):
        try:
            obj = json.loads(f.read_text(encoding="utf-8"))
            if obj.get("status") == "traduzido":
                translated += 1
        except:
            pass
            
    for f in es_dir.glob("*.json"):
        try:
            obj = json.loads(f.read_text(encoding="utf-8"))
            if obj.get("status") == "traduzido":
                translated += 1
        except:
            pass
            
    percent = (translated / total) * 100.0 if total > 0 else 0.0
    return {
        "total": total,
        "translated": translated,
        "percent": percent
    }

def format_eta(seconds):
    if seconds <= 0:
        return "Concluído"
    elif seconds > 3600:
        h = int(seconds // 3600)
        m = int((seconds % 3600) // 60)
        s = int(seconds % 60)
        return f"{h}h {m:02d}m {s:02d}s"
    elif seconds > 60:
        m = int(seconds // 60)
        s = int(seconds % 60)
        return f"{m:02d}m {s:02d}s"
    else:
        return f"{int(seconds)}s"

def ensure_environment(root):
    # 1. Garante que 00_original existe e verifica o arquivo base
    orig_dir = root / "00_original"
    orig_dir.mkdir(exist_ok=True)
    orig_file = orig_dir / "agenda_pt_original.json"
    if not orig_file.exists():
        print(f"\033[1;31mErro: Arquivo original não encontrado em: {orig_file}\033[0m")
        print("Por favor, coloque o arquivo 'agenda_pt_original.json' na pasta '00_original' e tente novamente.")
        sys.exit(1)
        
    # 2. Cria as pastas de trabalho se não existirem
    dias_pt = root / "01_dias_pt"
    trad_en = root / "02_traducao_en"
    trad_es = root / "03_traducao_es"
    saida = root / "04_saida_final"
    
    dias_pt.mkdir(exist_ok=True)
    trad_en.mkdir(exist_ok=True)
    trad_es.mkdir(exist_ok=True)
    saida.mkdir(exist_ok=True)
    
    # 3. Verifica se as pastas contêm arquivos JSON
    has_pt = any(dias_pt.glob("*.json"))
    has_en = any(trad_en.glob("*.json"))
    has_es = any(trad_es.glob("*.json"))
    
    if not (has_pt and has_en and has_es):
        print("\033[1;33mPastas de trabalho ausentes ou vazias. Inicializando banco de dados diário...\033[0m")
        try:
            # Adiciona o diretório dos scripts ao path para importação
            scripts_dir = Path(__file__).resolve().parent
            if str(scripts_dir) not in sys.path:
                sys.path.insert(0, str(scripts_dir))
            from recreate_dias import recreate_files
            recreate_files()
            print("\033[1;32mBanco de dados diário inicializado com sucesso!\033[0m")
        except Exception as e:
            print(f"\033[1;31mErro ao inicializar banco de dados diário: {e}\033[0m")
            sys.exit(1)
            
    # 4. Garante que traducao_status.csv exista
    csv_file = root / "traducao_status.csv"
    if not csv_file.exists():
        print("\033[1;33mPlanilha traducao_status.csv ausente. Gerando...\033[0m")
        update_status_csv(root)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--lang", choices=["en", "es", "both"], default="both")
    parser.add_argument("--limit", type=int, default=None)
    args = parser.parse_args()

    root = Path(__file__).resolve().parents[1]
    ensure_environment(root)
    
    en_dir = root / "02_traducao_en"
    es_dir = root / "03_traducao_es"
    pt_dir = root / "01_dias_pt"
    
    # Mapeia todas as datas do ano
    all_dates = sorted([f.stem for f in pt_dir.glob("*.json")])
    
    # Filtra datas pendentes baseado no idioma solicitado
    pending_dates = []
    for date_iso in all_dates:
        en_file = en_dir / f"{date_iso}.json"
        es_file = es_dir / f"{date_iso}.json"
        
        en_pending = False
        es_pending = False
        
        if args.lang in ("en", "both") and en_file.exists():
            try:
                obj = json.loads(en_file.read_text(encoding="utf-8"))
                if obj.get("status") == "pendente":
                    en_pending = True
            except:
                pass
                
        if args.lang in ("es", "both") and es_file.exists():
            try:
                obj = json.loads(es_file.read_text(encoding="utf-8"))
                if obj.get("status") == "pendente":
                    es_pending = True
            except:
                pass
                
        if en_pending or es_pending:
            pending_dates.append((date_iso, en_pending, es_pending))
            
    if args.limit:
        pending_dates = pending_dates[:args.limit]
        
    if not pending_dates:
        print("Tudo já está traduzido!")
        return

    # Estado interativo
    state = {
        "delay": 30.0,
        "paused": False,
        "current_step": "Iniciando...",
        "last_day_stats": None,
        "proc_times": [],
        "is_waiting": False
    }
    
    if not HAS_MSVCRT:
        print("\033[1;33m[Aviso] Módulo msvcrt não encontrado. O ajuste de delay via teclado (+/-) e pausa não estará ativo.\033[0m")
        time.sleep(2)
        
    for idx, (date_iso, en_pending, es_pending) in enumerate(pending_dates):
        day_start_time = time.time()
        pt_file = pt_dir / f"{date_iso}.json"
        en_file = en_dir / f"{date_iso}.json"
        es_file = es_dir / f"{date_iso}.json"
        
        orig = json.loads(pt_file.read_text(encoding="utf-8"))
        
        # Realiza traduções e atualiza o estado
        en_tr = None
        es_tr = None
        
        progress = get_progress_info(en_dir, es_dir)
        
        # Função para atualizar apenas a linha de status no rodapé (2 linhas com controle ANSI)
        def update_status(countdown):
            # Calcula o tempo total restante da execução deste lote (incluindo o tempo de processamento estimado)
            avg_proc = sum(state["proc_times"]) / len(state["proc_times"]) if state["proc_times"] else 8.0
            if state.get("is_waiting"):
                remaining_days_future = len(pending_dates) - 1 - idx
                eta_seconds = remaining_days_future * (state["delay"] + avg_proc) + countdown
            else:
                remaining_days_total = len(pending_dates) - idx
                eta_seconds = remaining_days_total * (state["delay"] + avg_proc)
                
            eta_seconds = max(0.0, eta_seconds)
            eta_str = format_eta(eta_seconds)
            
            status_str = "\033[1;31mPAUSADO (Pressione ESPAÇO para retomar)\033[0m" if state["paused"] else f"Próximo dia em \033[1;32m{countdown}s\033[0m"
            
            # Linha 1: Informações de Delay, Status e Tempo Restante Total
            line1 = f"Delay atual: \033[1;33m{int(state['delay'])}s\033[0m (Ajuste: '+' / '-') | Status: {status_str} | Tempo restante: \033[1;96m{eta_str}\033[0m"
            
            # Linha 2: Marcador gráfico do tempo do dia atual e Log de Passo
            total_delay = state["delay"]
            elapsed = total_delay - countdown
            bar_len = 20
            filled = int(bar_len * elapsed / total_delay) if total_delay > 0 else 0
            filled = max(0, min(bar_len, filled))
            c_bar = "█" * filled + "░" * (bar_len - filled)
            
            line2 = f"Tempo do dia: [{c_bar}] {int(countdown)}/{int(total_delay)}s | Passo: \033[1;97m{state['current_step']}\033[0m"
            
            # Escreve as duas linhas e move o cursor de volta para a Linha 1
            sys.stdout.write(f"\r{line1}\033[K\n{line2}\033[K\033[A")
            sys.stdout.flush()

        # Helper para re-renderizar a tela durante as etapas de processamento
        def step_callback(step_name):
            state["current_step"] = step_name
            draw_dashboard(date_iso, orig, en_tr, es_tr, state, progress)
            update_status(int(state["delay"]))
            
        step_callback("Carregando originais...")
        
        # 1. Tradução Inglês
        if en_pending:
            success, en_tr = process_file(en_file, "en", step_callback)
        else:
            try:
                en_tr = json.loads(en_file.read_text(encoding="utf-8")).get("traducao")
            except:
                pass
                
        # 2. Tradução Espanhol
        if es_pending:
            success, es_tr = process_file(es_file, "es", step_callback)
        else:
            try:
                es_tr = json.loads(es_file.read_text(encoding="utf-8")).get("traducao")
            except:
                pass
                
        # 3. Finalizações
        step_callback("Atualizando planilha de status...")
        update_status_csv(root)
        
        # Obtém progresso acumulado atualizado final
        progress = get_progress_info(en_dir, es_dir)
        
        # Mostra os painéis completos
        step_callback("Leitura do dia disponível.")
        
        # Fim do processamento, início do delay
        processing_end_time = time.time()
        proc_time = processing_end_time - day_start_time
        state["proc_times"].append(proc_time)
        
        # Executa o loop de delay interativo para este dia
        wait_with_interactive_keys(state, update_status)
        
        # Fim do dia, calcula estatísticas e salva no estado
        day_end_time = time.time()
        delay_time = day_end_time - processing_end_time
        total_time = day_end_time - day_start_time
        
        state["last_day_stats"] = {
            "date": date_iso,
            "total": total_time,
            "proc": proc_time,
            "delay": delay_time
        }
        
        # Move o cursor para abaixo da linha 2 e quebra linha antes do próximo dia
        sys.stdout.write("\n\n")
        sys.stdout.flush()

    print("\nTraduções completadas com sucesso!")

if __name__ == "__main__":
    main()
