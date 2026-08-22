import hashlib
import json
import os
from pathlib import Path

STORAGE_ROOT = Path.home() / "LifeOS_StorageStacks"
HASH_REGISTRY = {}
PRIMITIVES = []

SCAN_PATHS = [
    Path.home() / "Documents",
    Path.home() / "Desktop",
    Path.home() / "Downloads"
]

def calculate_sha256(file_path: Path) -> str:
    hasher = hashlib.sha256()
    with open(file_path, "rb") as f:
        while chunk := f.read(65536):
            hasher.update(chunk)
    return hasher.hexdigest()

def extract_file_primitives(file_path: Path, content_str: str) -> dict:
    mass = 1.0 + (file_path.stat().st_size / (1024 * 1024)) * 0.1
    flags = []
    
    for keyword in ["Kündigung", "Frist", "Rechnung", "Vertrag", "Mahnung", "IBAN", "DSGVO"]:
        if keyword.lower() in content_str.lower() or keyword.lower() in file_path.name.lower():
            mass += 1.5
            flags.append(keyword.upper())

    file_hash = calculate_sha256(file_path)
    vector = [
        round((int(file_hash[i:i+2], 16) / 255.0) * 2.0 - 1.0, 4)
        for i in range(0, 32, 2)
    ]

    return {
        "id": file_path.name,
        "sha256": file_hash,
        "mass": round(mass, 4),
        "flags": flags,
        "vector": vector,
        "snippet": content_str[:400]
    }

def run_genesis_extractor():
    STORAGE_ROOT.mkdir(parents=True, exist_ok=True)
    total_files = 0
    hardlinks_created = 0

    for base_path in SCAN_PATHS:
        if not base_path.exists():
            continue
        
        for file_path in base_path.rglob("*"):
            if not file_path.is_file() or file_path.name.startswith("."):
                continue

            if file_path.suffix.lower() not in [".txt", ".md", ".json", ".csv", ".pdf", ".rtf"]:
                continue

            total_files += 1
            file_hash = calculate_sha256(file_path)
            dest_file = STORAGE_ROOT / file_path.name

            if file_hash in HASH_REGISTRY:
                original = HASH_REGISTRY[file_hash]
                if not dest_file.exists():
                    try:
                        os.link(original, dest_file)
                        hardlinks_created += 1
                    except OSError:
                        dest_file.write_bytes(original.read_bytes())
                continue

            if not dest_file.exists():
                try:
                    dest_file.write_bytes(file_path.read_bytes())
                except Exception:
                    continue

            HASH_REGISTRY[file_hash] = dest_file
            text_content = ""
            try:
                text_content = dest_file.read_text(errors="ignore")
            except Exception:
                pass

            primitives = extract_file_primitives(dest_file, text_content)
            PRIMITIVES.append(primitives)

    summary_file = STORAGE_ROOT / "_genesis_summary.json"
    with open(summary_file, "w", encoding="utf-8") as f:
        json.dump(PRIMITIVES, f, ensure_ascii=False, indent=2)

if __name__ == "__main__":
    run_genesis_extractor()
