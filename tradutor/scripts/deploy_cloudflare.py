import os
import shutil
import subprocess
import sys
from concurrent.futures import ThreadPoolExecutor, as_completed

ROOT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
AUDIO_OUTPUT_DIR = os.path.join(ROOT_DIR, "audio", "output")
DADOS_DIR = os.path.join(os.path.dirname(ROOT_DIR), "dados")
PUBLIC_DIR = os.path.join(os.path.dirname(ROOT_DIR), "public")

R2_BUCKET_NAME = "agendadevocional"
PAGES_PROJECT_NAME = "agenda-devocional"

def run_command(cmd, cwd=None):
    print(f"Executing: {' '.join(cmd)}")
    result = subprocess.run(cmd, cwd=cwd, shell=True, capture_output=True, text=True, encoding='utf-8', errors='replace')
    if result.returncode != 0:
        print(f"Error executing command: {result.stderr}")
        return False, result.stderr
    return True, result.stdout

def prepare_public_folder():
    print("Preparing public folder...")
    if os.path.exists(PUBLIC_DIR):
        shutil.rmtree(PUBLIC_DIR)
    os.makedirs(PUBLIC_DIR, exist_ok=True)
    
    # Copy static assets
    parent_dir = os.path.dirname(ROOT_DIR)
    shutil.copy(os.path.join(parent_dir, "index.html"), os.path.join(PUBLIC_DIR, "index.html"))
    shutil.copy(os.path.join(parent_dir, "index.css"), os.path.join(PUBLIC_DIR, "index.css"))
    
    icon_path = os.path.join(parent_dir, "app_icon_google_play.png")
    if os.path.exists(icon_path):
        shutil.copy(icon_path, os.path.join(PUBLIC_DIR, "app_icon_google_play.png"))
        
    # Copy dados folder
    shutil.copytree(DADOS_DIR, os.path.join(PUBLIC_DIR, "dados"))
    print("Public folder prepared.")

def upload_file_to_r2(local_path, rel_path):
    # Command: npx wrangler r2 object put <bucket>/<key> --file=<local_path>
    key = rel_path.replace("\\", "/")
    cmd = [
        "npx", "wrangler", "r2", "object", "put",
        f"{R2_BUCKET_NAME}/{key}",
        f"--file={local_path}"
    ]
    # We suppress print per file to keep output clean, returning status
    result = subprocess.run(cmd, shell=True, capture_output=True, text=True, encoding='utf-8', errors='replace')
    return result.returncode == 0, key, result.stderr

def load_env_variables():
    # Simple parser to load .env file if it exists
    env_path = os.path.join(os.path.dirname(ROOT_DIR), ".env")
    if os.path.exists(env_path):
        with open(env_path, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line or line.startswith("#"):
                    continue
                if "=" in line:
                    key, val = line.split("=", 1)
                    os.environ[key.strip()] = val.strip()

def deploy_r2():
    if not os.path.exists(AUDIO_OUTPUT_DIR):
        print("No audio output folder found to upload to R2.")
        return

    load_env_variables()
    account_id = os.environ.get("CF_ACCOUNT_ID")
    access_key = os.environ.get("CF_R2_ACCESS_KEY_ID")
    secret_key = os.environ.get("CF_R2_SECRET_ACCESS_KEY")

    if account_id and access_key and secret_key:
        print(f"Syncing files to Cloudflare R2 using Rclone (Bucket: {R2_BUCKET_NAME})...")
        env = os.environ.copy()
        env["RCLONE_CONFIG_R2_TYPE"] = "s3"
        env["RCLONE_CONFIG_R2_PROVIDER"] = "Cloudflare"
        env["RCLONE_CONFIG_R2_ACCESS_KEY_ID"] = access_key
        env["RCLONE_CONFIG_R2_SECRET_ACCESS_KEY"] = secret_key
        env["RCLONE_CONFIG_R2_ENDPOINT"] = f"https://{account_id}.r2.cloudflarestorage.com"

        cmd = [
            "rclone", "sync",
            AUDIO_OUTPUT_DIR,
            f"r2:{R2_BUCKET_NAME}",
            "--progress"
        ]
        print(f"Executing: {' '.join(cmd)}")
        result = subprocess.run(cmd, env=env, shell=True)
        if result.returncode == 0:
            print("Rclone sync completed successfully!")
            return
        else:
            print(f"Rclone sync failed with exit code: {result.returncode}. Falling back to Wrangler...")

    print(f"Scanning files to upload to Cloudflare R2 via Wrangler (Bucket: {R2_BUCKET_NAME})...")
    print("Tip: To use fast incremental sync, add CF_ACCOUNT_ID, CF_R2_ACCESS_KEY_ID, and CF_R2_SECRET_ACCESS_KEY to a '.env' file.")
    upload_tasks = []
    
    for root, dirs, files in os.walk(AUDIO_OUTPUT_DIR):
        for file in files:
            if ".temp" in file or "temp" in file.lower():
                continue
            local_path = os.path.join(root, file)
            rel_path = os.path.relpath(local_path, AUDIO_OUTPUT_DIR)
            upload_tasks.append((local_path, rel_path))
            
    total_files = len(upload_tasks)
    print(f"Found {total_files} files to upload.")
    
    # Run uploads in parallel threads to speed up deployment
    completed = 0
    errors = []
    
    with ThreadPoolExecutor(max_workers=10) as executor:
        futures = {executor.submit(upload_file_to_r2, local, rel): (local, rel) for local, rel in upload_tasks}
        for future in as_completed(futures):
            success, key, err = future.result()
            completed += 1
            if success:
                # Simple progress indicator
                if completed % 50 == 0 or completed == total_files:
                    print(f"Uploaded {completed}/{total_files} files...")
            else:
                errors.append((key, err))
                
    print(f"R2 Sync finished. Successfully uploaded: {completed - len(errors)}/{total_files}")
    if errors:
        print(f"Encountered {len(errors)} errors during upload:")
        for key, err in errors[:10]:
            print(f"  - {key}: {err}")

def main():
    # 1. Generate Manifest
    print("--- Step 1: Generating Audio Manifest ---")
    manifest_script = os.path.join(ROOT_DIR, "scripts", "generate_audio_manifest.py")
    success, out = run_command(["python", manifest_script])
    if not success:
        sys.exit(1)
    print(out)
    
    # 2. Deploy Pages
    print("--- Step 2: Deploying to Cloudflare Pages ---")
    prepare_public_folder()
    success, out = run_command(["npx", "wrangler", "pages", "deploy", "public", f"--project-name={PAGES_PROJECT_NAME}"], cwd=os.path.dirname(ROOT_DIR))
    if not success:
        print("Pages deployment failed.")
    else:
        print(out)
        
    # 3. Deploy R2
    print("--- Step 3: Syncing Audios to Cloudflare R2 ---")
    deploy_r2()

if __name__ == "__main__":
    main()
