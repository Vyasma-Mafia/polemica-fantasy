#!/usr/bin/env python3
"""Remove background from all images in extracted_images/ and repack to new archive."""

import os
import shutil
import zipfile
from pathlib import Path
from PIL import Image
import io
from rembg import remove

INPUT_DIR = Path("extracted_images")
OUTPUT_DIR = Path("extracted_images_nobg")
ARCHIVE_NAME = "archive_nobg.zip"


def process_image(input_path: Path, output_path: Path) -> None:
    with Image.open(input_path) as img:
        img_bytes = io.BytesIO()
        img.save(img_bytes, format="PNG")
        img_bytes = img_bytes.getvalue()
    
    result = remove(img_bytes)
    
    with open(output_path, "wb") as f:
        f.write(result)


def main() -> None:
    if OUTPUT_DIR.exists():
        shutil.rmtree(OUTPUT_DIR)
    OUTPUT_DIR.mkdir()
    
    images = list(INPUT_DIR.glob("*.jpg")) + list(INPUT_DIR.glob("*.png"))
    print(f"Found {len(images)} images")
    
    for i, img_path in enumerate(images, 1):
        print(f"Processing {i}/{len(images)}: {img_path.name}", end=" ... ")
        output_path = OUTPUT_DIR / f"{img_path.stem}.png"
        try:
            process_image(img_path, output_path)
            print("OK")
        except Exception as e:
            print(f"ERROR: {e}")
    
    print(f"\nPacking to {ARCHIVE_NAME}...")
    with zipfile.ZipFile(ARCHIVE_NAME, "w", zipfile.ZIP_DEFLATED) as zf:
        for img_path in sorted(OUTPUT_DIR.glob("*.png")):
            zf.write(img_path, img_path.name)
    
    print(f"Done! Created {ARCHIVE_NAME}")
    print(f"Output directory: {OUTPUT_DIR}")


if __name__ == "__main__":
    main()
