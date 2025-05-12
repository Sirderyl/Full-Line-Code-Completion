import os, zipfile, io
import re
import math
from tqdm import tqdm
from datasets import load_dataset

OUTPUT_DIR = 'data/extracted_java_zip'
os.makedirs(OUTPUT_DIR, exist_ok=True)
FILES_PER_ZIP = 10000

def create_filename(repo_path, entry_id):
    # Extract class name from the path
    match = re.search(r'\/(\w+)\.java$', repo_path)
    if match:
        class_name = match.group(1)
    else:
        # Use ID as fallback
        class_name = f"JavaFile_{entry_id}"

    return f"{class_name}_{entry_id}.java"

# Get dataset size
ds = load_dataset("bigcode/starcoderdata", data_dir="java", split="train")
total_size = len(ds)
num_zips = math.ceil(total_size / FILES_PER_ZIP)
print(f"Datset contains {total_size} examples")
print(f"Will create {num_zips} zip files with {FILES_PER_ZIP} files each")

# Load as streaming dataset for memory viability
print("Extracting Java files ...")
streamed_ds = load_dataset("bigcode/starcoderdata", data_dir="java", split="train", streaming=True)

zip_idx = 0
count = 0
zf = None

with tqdm(total=total_size, desc="Creating zip shards") as pbar:
    for item in streamed_ds:
        if zf is None or count >= FILES_PER_ZIP:
            if zf:
                zf.close()
            zip_name = os.path.join(OUTPUT_DIR, f"part_{zip_idx:04d}.zip")
            zf = zipfile.ZipFile(zip_name, 'w', compression=zipfile.ZIP_STORED)
            zip_idx += 1
            count = 0

        repo_path = item['max_stars_repo_path']
        entry_id = item['id']
        filename = create_filename(repo_path, entry_id)

        zf.writestr(filename, item['content'])
        count += 1
        pbar.update(1)

    if zf:
        zf.close()
