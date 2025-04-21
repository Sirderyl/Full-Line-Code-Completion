import os
import re
from tqdm import tqdm
from datasets import load_dataset

output_dir = 'data/extracted_java'
os.makedirs(output_dir, exist_ok=True)

def create_filename(repo_path, entry_id):
    # Extract class name from the path
    match = re.search(r'\/(\w+)\.java$', repo_path)
    if match:
        class_name = match.group(1)
    else:
        # Use ID as fallback
        class_name = f"JavaFile_{entry_id}"

    return os.path.join(output_dir, f"{class_name}_{entry_id}.java")

# Process a batch of examples for performance
def process_batch(dataset, total_size):
    count = 0
    with tqdm(total=total_size) as pbar:
        for example in dataset:
            path = example['max_stars_repo_path']
            content = example['content']
            entry_id = example['id']
            
            output_file = create_filename(path, entry_id)
            with open(output_file, 'w', encoding='utf-8') as f:
                f.write(content)
            
            count += 1
            pbar.update(1)
    
    return count

if __name__ == "__main__":
    # Get dataset size
    ds = load_dataset("bigcode/starcoderdata", data_dir="java", split="train")
    total_size = len(ds)
    print(f"Datset contains {total_size} examples")

    # Load as streaming dataset for memory viability
    print("Extracting Java files ...")
    streamed_ds = load_dataset("bigcode/starcoderdata", data_dir="java", split="train", streaming=True)
    
    processed = process_batch(streamed_ds, total_size)
    print(f"Extracted {processed} Java files to {output_dir}")