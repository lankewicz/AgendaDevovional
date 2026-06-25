import urllib.request
import json
import ssl
import os

ctx = ssl._create_unverified_context()

# Target directory inside project
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
CACHE_DIR = os.path.join(os.path.dirname(SCRIPT_DIR), "biblia")

urls = {
    "arc": ("https://raw.githubusercontent.com/maatheusgois/bible/main/versions/pt-br/arc.json", "arc.json"),
    "web": ("https://raw.githubusercontent.com/dscottpi/bibles/master/WEB.json", "web.json"),
    "rv1960": ("https://raw.githubusercontent.com/dscottpi/bibles/master/RVR1960-Spanish.json", "rv1960.json")
}


# Book abbreviations mapping
ABBREVIATIONS = {
    "Genesis": "Gn", "Exodus": "Ex", "Leviticus": "Lv", "Numbers": "Nm", "Deuteronomy": "Dt",
    "Joshua": "Jos", "Judges": "Jg", "Ruth": "Rt", "1 Samuel": "1Sm", "2 Samuel": "2Sm",
    "1 Kings": "1Ki", "2 Kings": "2Ki", "1 Chronicles": "1Ch", "2 Chronicles": "2Ch",
    "Ezra": "Ezr", "Nehemiah": "Ne", "Esther": "Est", "Job": "Jb", "Psalms": "Ps",
    "Proverbs": "Pr", "Ecclesiastes": "Ec", "Song of Solomon": "So", "Isaiah": "Is",
    "Jeremiah": "Jr", "Lamentations": "Lm", "Ezekiel": "Ez", "Daniel": "Dn", "Hosea": "Ho",
    "Joel": "Jl", "Amos": "Am", "Obadiah": "Ob", "Jonah": "Jon", "Micah": "Mc",
    "Nahum": "Na", "Habakkuk": "Hb", "Zephaniah": "Zp", "Haggai": "Hg", "Zechariah": "Zc",
    "Malachi": "Ml", "Matthew": "Mt", "Mark": "Mk", "Luke": "Lk", "John": "Jn",
    "Acts": "Ac", "Romans": "Rm", "1 Corinthians": "1Co", "2 Corinthians": "2Co",
    "Galatians": "Ga", "Ephesians": "Ep", "Philippians": "Ph", "Colossians": "Cl",
    "1 Thessalonians": "1Th", "2 Thessalonians": "2Th", "1 Timothy": "1Ti", "2 Timothy": "2Ti",
    "Titus": "Tt", "Philemon": "Phm", "Hebrews": "Hb", "James": "Jas", "1 Peter": "1Pe",
    "2 Peter": "2Pe", "1 John": "1Jn", "2 John": "2Jn", "3 John": "3Jn", "Jude": "Jud",
    "Revelation": "Rv",
    
    # Spanish Book names
    "Génesis": "Gn", "Éxodo": "Ex", "Levítico": "Lv", "Números": "Nm", "Deuteronomio": "Dt",
    "Josué": "Jos", "Jueces": "Jg", "Rut": "Rt", "Nehemías": "Ne", "Job": "Jb", "Salmos": "Ps",
    "Proverbios": "Pr", "Cantares": "So", "Isaías": "Is", "Jeremías": "Jr", "Lamentaciones": "Lm",
    "Ezequiel": "Ez", "Miqueas": "Mc", "Sofonías": "Zp", "Hageo": "Hg", "Zacarías": "Zc",
    "Malaquías": "Ml", "Mateo": "Mt", "Marcos": "Mk", "Lucas": "Lk", "Juan": "Jn",
    "Hechos": "Ac", "Romanos": "Rm", "1 Corintios": "1Co", "2 Corintios": "2Co",
    "Gálatas": "Ga", "Efesios": "Ep", "1 Tesalonicenses": "1Th", "2 Tesalonicenses": "2Th",
    "Tito": "Tt", "Filemón": "Phm", "Hebreos": "Hb", "Santiago": "Jas", "1 Pedro": "1Pe",
    "2 Pedro": "2Pe", "1 Juan": "1Jn", "2 Juan": "2Jn", "3 Juan": "3Jn", "Judas": "Jud",
    "Apocalipsis": "Rv"
}

def clean_spanish_key(key):
    # Map Gospel names and fix encoding errors in keys
    fixes = {
        "S. Lucas": "Lucas",
        "S. Marcos": "Marcos",
        "S. Mateo": "Mateo",
        "S.Juan": "Juan",
        "Gnesis": "Génesis",
        "xodo": "Éxodo",
        "Levtico": "Levítico",
        "Nmeros": "Números",
        "Nehemas": "Nehemías",
        "Isaas": "Isaías",
        "Jeremas": "Jeremías",
        "Lamentaciones": "Lamentaciones",
        "Miqueas": "Miqueas",
        "Sofonas": "Sofonías",
        "Zacaras": "Zacarías",
        "Malaquas": "Malaquías",
        "Hechos": "Hechos",
        "1 Crnicas": "1 Crónicas",
        "2 Crnicas": "2 Crónicas",
        "1 Tesalonicenses": "1 Tesalonicenses",
        "2 Tesalonicenses": "2 Tesalonicenses",
        "Filemn": "Filemón",
        "Apocalipsis": "Apocalipsis",
        "1 Pedro": "1 Pedro",
        "2 Pedro": "2 Pedro",
        "1 Juan": "1 Juan",
        "2 Juan": "2 Juan",
        "3 Juan": "3 Juan"
    }
    for bad, good in fixes.items():
        if bad in key:
            key = key.replace(bad, good)
    return key

CLASSICAL_ORDER_EN = [
    "genesis", "exodus", "leviticus", "numbers", "deuteronomy", "joshua", "judges", "ruth",
    "1samuel", "2samuel", "1kings", "2kings", "1chronicles", "2chronicles", "ezra", "nehemiah",
    "esther", "job", "psalms", "proverbs", "ecclesiastes", "songofsongs", "isaiah", "jeremiah",
    "lamentations", "ezekiel", "daniel", "hosea", "joel", "amos", "obadiah", "jonah", "micah",
    "nahum", "habakkuk", "zephaniah", "haggai", "zechariah", "malachi", "matthew", "mark", "luke",
    "john", "acts", "romans", "1corinthians", "2corinthians", "galatians", "ephesians", "philippians",
    "colossians", "1thessalonians", "2thessalonians", "1timothy", "2timothy", "titus", "philemon",
    "hebrews", "james", "1peter", "2peter", "1john", "2john", "3john", "jude", "revelation"
]

CLASSICAL_ORDER_ES = [
    "genesis", "exodo", "levitico", "numeros", "deuteronomio", "josue", "jueces", "rut",
    "1samuel", "2samuel", "1reyes", "2reyes", "1cronicas", "2cronicas", "esdras", "nehemias",
    "ester", "job", "salmos", "proverbios", "eclesiastes", "cantares", "isaias", "jeremias",
    "lamentaciones", "ezequiel", "daniel", "oseas", "joel", "amos", "abdias", "jonas", "miqueas",
    "nahum", "habacuc", "sofonias", "hageo", "zacarias", "malaquias", "mateo", "marcos", "lucas",
    "juan", "hechos", "romanos", "1corintios", "2corintios", "galatas", "efesios", "filipenses",
    "colosenses", "1tesalonicenses", "2tesalonicenses", "1timoteo", "2timoteo", "tito", "filemon",
    "hebreos", "santiago", "1pedro", "2pedro", "1juan", "2juan", "3juan", "judas", "apocalipsis"
]


import unicodedata
import re

def normalize_name(name):
    name = name.lower()
    # Remove accents
    name = "".join(c for c in unicodedata.normalize('NFD', name) if unicodedata.category(c) != 'Mn')
    # Remove non-alphanumeric
    name = re.sub(r'[^a-z0-9]', '', name)
    # Map Spanish Gospels
    mapping = {
        "smateo": "mateo",
        "smarcos": "marcos",
        "slucas": "lucas",
        "sjuan": "juan"
    }
    return mapping.get(name, name)

# Build classical index lookup map
CLASSICAL_MAP = {}
for i, (en, es) in enumerate(zip(CLASSICAL_ORDER_EN, CLASSICAL_ORDER_ES)):
    CLASSICAL_MAP[en] = i
    CLASSICAL_MAP[es] = i

def get_book_sort_key(item):
    book_name = item[0]
    norm = normalize_name(book_name)
    return CLASSICAL_MAP.get(norm, 999)

def download_and_convert():
    os.makedirs(CACHE_DIR, exist_ok=True)
    
    for version_name, (url, filename) in urls.items():
        dest_path = os.path.join(CACHE_DIR, filename)
        print(f"Downloading {version_name} from {url}...")
        
        try:
            req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
            with urllib.request.urlopen(req, context=ctx) as response:
                raw_bytes = response.read()
                
                # Try decoding as utf-8, fallback to cp1252/latin1
                try:
                    raw_text = raw_bytes.decode('utf-8')
                except UnicodeDecodeError:
                    raw_text = raw_bytes.decode('cp1252')
                raw_data = json.loads(raw_text)
            
            # If the format is already a list (e.g. arc.json), save it directly
            if isinstance(raw_data, list):
                print(f"Saving {version_name} directly (already a list)...")
                with open(dest_path, "w", encoding="utf-8") as f:
                    json.dump(raw_data, f, ensure_ascii=False, indent=2)
                print(f"Successfully saved {version_name} to: {dest_path}")
                continue
                
            print(f"Converting {version_name} data structure and sorting by classical order...")
            converted_list = []
            
            # Sort books to maintain classical Bible order
            for book_name, chapters_dict in sorted(raw_data.items(), key=get_book_sort_key):

                if not isinstance(chapters_dict, dict):
                    print(f"  Skipping non-book key: {book_name}")
                    continue
                
                cleaned_book_name = clean_spanish_key(book_name)
                
                # Convert chapters dict { "1": { "1": "text", "2": "text" } } to list of lists
                chapters_list = []
                
                # Chapters are keys like "1", "2", ...
                # Sort numerically
                sorted_chap_keys = sorted(chapters_dict.keys(), key=lambda x: int(x))
                
                for chap_key in sorted_chap_keys:
                    verses_dict = chapters_dict[chap_key]
                    verses_list = []
                    
                    # Verses are keys like "1", "2", ...
                    # Sort numerically
                    sorted_verse_keys = sorted(verses_dict.keys(), key=lambda x: int(x))
                    
                    for verse_key in sorted_verse_keys:
                        verse_text = verses_dict[verse_key]
                        # Strip extra spaces
                        verse_text = " ".join(verse_text.split())
                        verses_list.append(verse_text)
                        
                    chapters_list.append(verses_list)
                
                abbrev = ABBREVIATIONS.get(cleaned_book_name, cleaned_book_name[:3])
                converted_list.append({
                    "name": cleaned_book_name,
                    "abbrev": abbrev,
                    "chapters": chapters_list
                })
            
            with open(dest_path, "w", encoding="utf-8") as f:
                json.dump(converted_list, f, ensure_ascii=False, indent=2)
                
            print(f"Successfully saved converted {version_name} to: {dest_path}")
            
        except Exception as e:
            print(f"Error processing {version_name}: {e}")


if __name__ == "__main__":
    download_and_convert()
