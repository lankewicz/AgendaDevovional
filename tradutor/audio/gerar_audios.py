import asyncio
import os
import sys
import argparse
import subprocess
import edge_tts

# Voice mapping
VOICES = {
    "pt": "pt-BR-FranciscaNeural",
    "en": "en-US-GuyNeural",
    "es": "es-ES-AlvaroNeural"
}

import shutil

async def text_to_speech_m4a(text: str, lang: str, output_path: str, voice: str = None, rate: str = "+0%", on_status_change=None):
    """
    Generates an MP3 using edge-tts and converts it to optimized M4A (AAC) using FFmpeg.
    Falls back to saving raw MP3 if FFmpeg is not found.
    """
    if not voice:
        voice = VOICES.get(lang, VOICES["pt"])
    
    # Ensure directory exists
    output_dir = os.path.dirname(output_path)
    if output_dir:
        os.makedirs(output_dir, exist_ok=True)

    # Check for FFmpeg availability
    ffmpeg_bin = "ffmpeg"
    fallback_ffmpeg = r"C:\Users\vl097\AppData\Local\Microsoft\WinGet\Packages\Gyan.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe\ffmpeg-8.1.1-full_build\bin\ffmpeg.exe"
    ffmpeg_available = shutil.which("ffmpeg") is not None
    if not ffmpeg_available and os.path.exists(fallback_ffmpeg):
        ffmpeg_available = True
        ffmpeg_bin = fallback_ffmpeg

    if on_status_change:
        await on_status_change("Criando (TTS)")

    if not ffmpeg_available:
        # Fallback path changing extension to .mp3
        fallback_path = os.path.splitext(output_path)[0] + ".mp3"
        print(f"Warning: FFmpeg not found in PATH. Saving as MP3 instead: {fallback_path}")
        print(f"Generating MP3 using voice: {voice} and rate: {rate}...")
        communicate = edge_tts.Communicate(text, voice, rate=rate)
        await communicate.save(fallback_path)
        print(f"Success! Audio saved to: {fallback_path}")
        return fallback_path

    temp_mp3 = output_path + ".temp.mp3"

    print(f"Generating temporary MP3 using voice: {voice} and rate: {rate}...")
    communicate = edge_tts.Communicate(text, voice, rate=rate)
    await communicate.save(temp_mp3)

    if not os.path.exists(temp_mp3):
        raise RuntimeError("Failed to generate temporary MP3 file.")

    if on_status_change:
        await on_status_change("Convertendo (M4A)")

    print(f"Converting to M4A using FFmpeg (AAC, 32kbps, Mono)...")
    ffmpeg_cmd = [
        ffmpeg_bin,
        "-y",               # Overwrite output files without asking
        "-i", temp_mp3,     # Input file
        "-c:a", "aac",      # Audio codec AAC
        "-b:a", "32k",      # Bitrate 32kbps
        "-ac", "1",         # Channels: 1 (Mono)
        output_path         # Output path
    ]

    try:
        # Run FFmpeg conversion
        result = subprocess.run(ffmpeg_cmd, capture_output=True, text=True, check=True)
        if on_status_change:
            await on_status_change("Salvando")
        print(f"Success! Audio saved to: {output_path}")
        return output_path
    except subprocess.CalledProcessError as e:
        print(f"FFmpeg conversion error: {e.stderr}", file=sys.stderr)
        raise e
    finally:
        # Clean up temporary file with retries for Windows file locking
        if os.path.exists(temp_mp3):
            for _ in range(5):
                try:
                    os.remove(temp_mp3)
                    break
                except PermissionError:
                    await asyncio.sleep(0.2)
                except Exception:
                    break

def main():
    parser = argparse.ArgumentParser(description="Generate optimized M4A audio using Edge-TTS and FFmpeg.")
    parser.add_argument("--text", type=str, help="Text to convert to speech.")
    parser.add_argument("--lang", type=str, default="pt", choices=["pt", "en", "es"], help="Language code (pt, en, es).")
    parser.add_argument("--voice", type=str, default=None, help="Specific Edge-TTS voice to use.")
    parser.add_argument("--rate", type=str, default="+0%", help="Voice rate/speed (e.g. +0%, -15%).")
    parser.add_argument("--output", type=str, required=True, help="Output path for the .m4a file.")

    args = parser.parse_args()

    if not args.text:
        # If no text is provided on CLI, read from stdin (allows piping)
        args.text = sys.stdin.read().strip()

    if not args.text:
        print("Error: No text provided.", file=sys.stderr)
        sys.exit(1)

    asyncio.run(text_to_speech_m4a(args.text, args.lang, args.output, args.voice, args.rate))

if __name__ == "__main__":
    main()
