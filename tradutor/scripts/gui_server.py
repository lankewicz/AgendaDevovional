import http.server
import socketserver
import json
import os
import re
import unicodedata
import urllib.parse
import sys

# Port configuration
PORT = 8080

# Paths
ROOT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GUI_DIR = os.path.join(ROOT_DIR, "scripts", "gui")
AGENDA_2027_PATH = os.path.join(ROOT_DIR, "2027", "Agenda_2027.json")
DEVOCIONAIS_2027_PATH = os.path.join(ROOT_DIR, "2027", "devocionais_2027_unificado_internacional.json")
DADOS_2027_PATH = os.path.join(os.path.dirname(ROOT_DIR), "dados", "agenda_2027_pt.json")

# ARC Cached Bible Path
ARC_CACHE_PATH = r"C:\Users\vl097\.gemini\antigravity-ide\brain\ba65a363-4da8-45ee-8829-e021a6c0fedf\scratch\arc.json"

# Helper functions for reference parsing and normalization
def normalize_text(text):
    if not text:
        return ""
    text = text.lower()
    text = "".join(c for c in unicodedata.normalize('NFD', text) if unicodedata.category(c) != 'Mn')
    text = re.sub(r'[^a-z0-9]', '', text)
    return text

def parse_reference(ref_str):
    ref_str = ref_str.strip()
    match = re.match(r'^(.+?)\s+(\d+):([\d\-a-zA-Z]+)$', ref_str)
    if not match:
        return None
    book = match.group(1).strip()
    chapter = int(match.group(2))
    verses_str = match.group(3).strip()
    verses_str = re.sub(r'[a-zA-Z]$', '', verses_str)
    
    verses = []
    if '-' in verses_str:
        start_v, end_v = verses_str.split('-')
        for v in range(int(start_v), int(end_v) + 1):
            verses.append(v)
    else:
        verses.append(int(verses_str))
        
    return book, chapter, verses

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
            else:
                print(f"Warning: Bible for {lang} not found at {path}")

load_bibles()

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

def find_devotional_path(dia_id):
    expected_filename = f"dia_{dia_id:03d}.json"
    devocionais_dir = os.path.join(ROOT_DIR, "devocionais")
    for root, dirs, files in os.walk(devocionais_dir):
        if expected_filename in files:
            return os.path.join(root, expected_filename)
    return os.path.join(devocionais_dir, expected_filename)  # Fallback

# Load ARC Bible for backwards-compatible get_arc_verse lookup
def load_arc_bible():
    local_arc_path = os.path.join(ROOT_DIR, "biblia", "arc.json")
    path_to_use = local_arc_path if os.path.exists(local_arc_path) else ARC_CACHE_PATH
    if not os.path.exists(path_to_use):
        print(f"Error: ARC Bible not found at {path_to_use}. Run download script first.")
        sys.exit(1)
    with open(path_to_use, "r", encoding="utf-8-sig") as f:
        data = json.load(f)
    
    # Build lookup
    lookup = {}
    for book in data:
        book_name_norm = normalize_text(book["name"])
        lookup[book_name_norm] = {
            "name": book["name"],
            "chapters": book["chapters"]
        }
    return lookup

arc_lookup = load_arc_bible()

def get_arc_verse(ref_str):
    parsed = parse_reference(ref_str)
    if not parsed:
        return ""
    book, chapter, verses = parsed
    book_norm = normalize_text(book)
    
    # Book mapping
    matched_key = None
    if book_norm in arc_lookup:
        matched_key = book_norm
    else:
        for k in arc_lookup.keys():
            if book_norm in k or k in book_norm:
                matched_key = k
                break
                
    if not matched_key:
        return ""
        
    chapters = arc_lookup[matched_key]["chapters"]
    if chapter > len(chapters):
        return ""
        
    chapter_verses = chapters[chapter - 1]
    verse_parts = []
    for v in verses:
        if v > len(chapter_verses):
            break
        verse_parts.append(chapter_verses[v - 1])
        
    return " ".join(verse_parts)

class CustomHTTPRequestHandler(http.server.SimpleHTTPRequestHandler):
    def translate_path(self, path):
        # Serve GUI directory for frontend requests
        parsed_url = urllib.parse.urlparse(path)
        clean_path = parsed_url.path
        if clean_path == "/" or clean_path == "/index.html":
            return os.path.join(GUI_DIR, "index.html")
        if clean_path == "/devocionais.html":
            return os.path.join(GUI_DIR, "devocionais.html")
        return os.path.join(GUI_DIR, clean_path.lstrip("/"))

    def get_source_path(self, query_params):
        source_id = query_params.get("source", ["agenda_2027"])[0]
        if source_id == "devocionais_2027":
            return DEVOCIONAIS_2027_PATH, "devocionais_2027_unificado_internacional.json"
        elif source_id == "dados_2027":
            return DADOS_2027_PATH, "agenda_2027_pt.json (dados)"
        elif source_id == "agenda_2027":
            return AGENDA_2027_PATH, "Agenda_2027.json"
        
        # If it doesn't match the predefined IDs, treat it as an absolute or relative path
        if os.path.isabs(source_id):
            return source_id, os.path.basename(source_id)
        
        # Try relative to parent of ROOT_DIR (e.g. Agenda/) or ROOT_DIR itself
        parent_dir = os.path.dirname(ROOT_DIR)
        path_in_parent = os.path.abspath(os.path.join(parent_dir, source_id))
        if os.path.exists(path_in_parent) or source_id.startswith("dados/"):
            return path_in_parent, os.path.basename(path_in_parent)
            
        path_in_root = os.path.abspath(os.path.join(ROOT_DIR, source_id))
        return path_in_root, os.path.basename(path_in_root)

    def do_GET(self):
        parsed_url = urllib.parse.urlparse(self.path)
        query_params = urllib.parse.parse_qs(parsed_url.query)
        
        # API: Get devotional by day
        match_dev = re.match(r"^/api/devocionais/dia/(\d+)$", parsed_url.path)
        # API: Get Bible text
        match_bible = re.match(r"^/api/biblia/(pt|en|es)/([^/]+)/(\d+)$", parsed_url.path)
        # Static Audio Serving
        is_audio_path = parsed_url.path.startswith("/audio/")

        if match_dev:
            dia_id = int(match_dev.group(1))
            file_path = find_devotional_path(dia_id)
            
            if not os.path.exists(file_path):
                self.send_response(404)
                self.end_headers()
                return

            self.send_response(200)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.end_headers()
            with open(file_path, "r", encoding="utf-8") as f:
                self.wfile.write(f.read().encode("utf-8"))

        elif match_bible:
            lang = match_bible.group(1)
            book_id = match_bible.group(2)
            chapter = int(match_bible.group(3))
            
            text = get_bible_text(lang, book_id, chapter)
            
            self.send_response(200)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.end_headers()
            self.wfile.write(json.dumps({"text": text}, ensure_ascii=False).encode("utf-8"))

        elif is_audio_path:
            # Serve audio files from audio/output directory
            rel_path = parsed_url.path.replace("/audio/", "", 1)
            target_path = os.path.abspath(os.path.join(ROOT_DIR, "audio", "output", rel_path))
            
            # Prevent directory traversal
            allowed_dir = os.path.abspath(os.path.join(ROOT_DIR, "audio", "output"))
            if not target_path.startswith(allowed_dir):
                self.send_response(403)
                self.end_headers()
                return

            if not os.path.exists(target_path):
                self.send_response(404)
                self.end_headers()
                return

            content_type = "audio/mp4" if target_path.endswith(".m4a") else "audio/mpeg"
            self.send_response(200)
            self.send_header("Content-Type", content_type)
            self.send_header("Content-Length", str(os.path.getsize(target_path)))
            self.end_headers()
            with open(target_path, "rb") as f:
                self.wfile.write(f.read())

        elif parsed_url.path == "/api/devocionais":
            source_path, source_name = self.get_source_path(query_params)
            
            if not os.path.exists(source_path):
                self.send_response(404)
                self.send_header("Content-Type", "application/json")
                self.end_headers()
                self.wfile.write(json.dumps({"error": f"Arquivo fonte {source_name} não encontrado."}).encode('utf-8'))
                return
                
            self.send_response(200)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.end_headers()
            
            # Read selected Agenda
            with open(source_path, "r", encoding="utf-8") as f:
                agenda = json.load(f)
                
            # Merge with ARC verses
            for item in agenda:
                ref = item.get("referencia", "")
                item["arc_versiculo"] = get_arc_verse(ref)
                
            self.wfile.write(json.dumps(agenda, ensure_ascii=False, indent=2).encode('utf-8'))
        else:
            super().do_GET()

    def do_POST(self):
        parsed_url = urllib.parse.urlparse(self.path)
        query_params = urllib.parse.parse_qs(parsed_url.query)
        
        match_salvar = re.match(r"^/api/devocionais/dia/(\d+)/salvar$", parsed_url.path)
        match_gerar_audio = re.match(r"^/api/devocionais/dia/(\d+)/gerar-audio$", parsed_url.path)

        if match_salvar:
            dia_id = int(match_salvar.group(1))
            content_length = int(self.headers['Content-Length'])
            post_data = self.rfile.read(content_length)
            
            try:
                devocional_json = json.loads(post_data.decode('utf-8'))
                file_path = find_devotional_path(dia_id)
                with open(file_path, "w", encoding="utf-8") as f:
                    json.dump(devocional_json, f, ensure_ascii=False, indent=2)
                
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.end_headers()
                self.wfile.write(json.dumps({"success": True}).encode('utf-8'))
            except Exception as e:
                self.send_response(500)
                self.end_headers()
                self.wfile.write(json.dumps({"error": str(e)}).encode('utf-8'))

        elif match_gerar_audio:
            dia_id = int(match_gerar_audio.group(1))
            content_length = int(self.headers['Content-Length'])
            post_data = self.rfile.read(content_length)
            
            try:
                payload = json.loads(post_data.decode('utf-8'))
                lang = payload.get("lang", "pt")
                audio_type = payload.get("type", "devocional")
                rate = payload.get("rate", "+0%")
                voice = payload.get("voice", "")
                
                if not voice:
                    # Default voice if not specified
                    voice_mapping = {"pt": "pt-BR-FranciscaNeural", "en": "en-US-GuyNeural", "es": "es-ES-AlvaroNeural"}
                    voice = voice_mapping.get(lang, "pt-BR-FranciscaNeural")

                # Load existing devotional JSON
                dev_path = find_devotional_path(dia_id)
                if not os.path.exists(dev_path):
                    raise FileNotFoundError(f"Devocional para o dia {dia_id} não encontrado.")
                
                with open(dev_path, "r", encoding="utf-8") as f:
                    dev_json = json.load(f)

                # Initialize audio structure in JSON
                if "audio" not in dev_json:
                    dev_json["audio"] = {}
                if lang not in dev_json["audio"]:
                    dev_json["audio"][lang] = {}
                if voice not in dev_json["audio"][lang]:
                    dev_json["audio"][lang][voice] = {}

                script_path = os.path.join(ROOT_DIR, "audio", "gerar_audios.py")
                output_dir = os.path.join(ROOT_DIR, "audio", "output")
                os.makedirs(output_dir, exist_ok=True)
                
                import subprocess

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
                            output_filename = f"{chapter:03d}.m4a"
                            output_path_m4a = os.path.join(output_dir, output_rel_dir, output_filename)
                            
                            # Run edge-tts generator subprocess
                            cmd = ["python", script_path, "--text", text, "--lang", lang, "--output", output_path_m4a, f"--rate={rate}", f"--voice={voice}"]
                            subprocess.run(cmd, capture_output=True, text=True, check=True)
                            
                            dev_json["audio"][lang][voice]["biblia"][f"{book_id}_{chapter}"] = f"audio/biblia/{book_id}/{lang}/{voice}/{chapter:03d}.m4a"
                else:
                    # Determine the text to generate audio for based on audio_type
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
                    else:
                        raise ValueError(f"Tipo de áudio inválido: {audio_type}")

                    if not text or not text.strip():
                        raise ValueError(f"O texto para '{audio_type}' no idioma '{lang}' está vazio.")

                    output_rel_dir = os.path.join("dia", f"{dia_id:03d}", lang, voice)
                    output_filename = f"{audio_type}.m4a"
                    output_path_m4a = os.path.join(output_dir, output_rel_dir, output_filename)
                    
                    # Run edge-tts generator subprocess
                    cmd = ["python", script_path, "--text", text, "--lang", lang, "--output", output_path_m4a, f"--rate={rate}", f"--voice={voice}"]
                    subprocess.run(cmd, capture_output=True, text=True, check=True)
                    
                    dev_json["audio"][lang][voice][audio_type] = f"audio/dia/{dia_id:03d}/{lang}/{voice}/{audio_type}.m4a"
                
                with open(dev_path, "w", encoding="utf-8") as f:
                    json.dump(dev_json, f, ensure_ascii=False, indent=2)

                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.end_headers()
                self.wfile.write(json.dumps({"success": True}).encode('utf-8'))
                
            except Exception as e:
                self.send_response(500)
                self.send_header("Content-Type", "application/json")
                self.end_headers()
                self.wfile.write(json.dumps({"success": False, "error": str(e)}).encode('utf-8'))

        elif parsed_url.path == "/api/salvar":
            content_length = int(self.headers['Content-Length'])
            post_data = self.rfile.read(content_length)
            
            try:
                updated_devocionais = json.loads(post_data.decode('utf-8'))
                source_path, source_name = self.get_source_path(query_params)
                
                # Strip helper field "arc_versiculo" and "is_correct" before saving
                cleaned_devocionais = []
                for item in updated_devocionais:
                    cleaned_item = {
                        "data": item.get("data", ""),
                        "versiculo": item.get("versiculo", ""),
                        "referencia": item.get("referencia", ""),
                        "contexto": item.get("contexto", ""),
                        "significado": item.get("significado", ""),
                        "mensagem": item.get("mensagem", ""),
                        "mantido": item.get("mantido", False)
                    }
                    cleaned_devocionais.append(cleaned_item)
                
                # Write specifically to the selected source path
                with open(source_path, "w", encoding="utf-8") as f:
                    json.dump(cleaned_devocionais, f, ensure_ascii=False, indent=2)
                
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.end_headers()
                self.wfile.write(json.dumps({"success": True}).encode('utf-8'))
                print(f"Successfully saved updated devocionais to source: {source_name}")
                
            except Exception as e:
                print(f"Error saving data: {e}")
                self.send_response(500)
                self.send_header("Content-Type", "application/json")
                self.end_headers()
                self.wfile.write(json.dumps({"success": False, "error": str(e)}).encode('utf-8'))
        else:
            self.send_response(404)
            self.end_headers()

def run_server():
    # Make sure GUI_DIR exists
    os.makedirs(GUI_DIR, exist_ok=True)
    
    handler = CustomHTTPRequestHandler
    with socketserver.TCPServer(("", PORT), handler) as httpd:
        print(f"\n=======================================================")
        print(f"  Comparador e Corretor ARC — Servidor Iniciado")
        print(f"  Acesse no seu navegador: http://localhost:{PORT}/")
        print(f"  Pressione Ctrl+C para encerrar o servidor.")
        print(f"=======================================================\n")
        try:
            httpd.serve_forever()
        except KeyboardInterrupt:
            print("\nServidor encerrado.")
            httpd.server_close()

if __name__ == "__main__":
    run_server()
