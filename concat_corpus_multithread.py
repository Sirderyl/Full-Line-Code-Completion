import os
from concurrent.futures import ProcessPoolExecutor, as_completed
import multiprocessing
from tqdm import tqdm
import time
import glob
import math

base_dir = "./data/processed_dataset"
output = "./data/corpus_unicode.txt"
BATCH_SIZE = 10000
SLICE = 5   # Specify the percentage of directories to process

def process_java_file(java_file):
    """Process a single Java file and return its content."""
    try:
        with open(java_file, 'r', encoding='utf-8') as f_java:
            content = f_java.read()
            # Strip indentation and trailing whitespace and remove empty lines
            lines = [line.strip() for line in content.splitlines() if line.strip()]
            if lines:
                return "\n".join(lines) + "\n"
    except Exception as e:
        return f"Error reading {java_file}: {e}\n"
    return ""

def process_batch(java_files_batch, pbar, outfile):
    with ProcessPoolExecutor(max_workers=multiprocessing.cpu_count()) as executor:
        # Submit batch tasks
        future_to_file = {executor.submit(process_java_file, java_file): java_file for java_file in java_files_batch}

        for future in as_completed(future_to_file):
            content = future.result()
            if content and not content.startswith("Error reading"):
                outfile.write(content)
            elif content.startswith("Error reading"):
                tqdm.write(content.strip())

            # Update progress bar
            pbar.update(1)

def main():
    print("Collecting directories...")
    processed_dirs = glob.glob(os.path.join(base_dir, "processed_part_*"))
    processed_dirs.sort()

    print(f"Found {len(processed_dirs)} directories.")

    # Calculate the number of directories to process based on SLICE percentage
    num_dirs_to_use = math.ceil(len(processed_dirs) * SLICE / 100)
    selected_dirs = processed_dirs[:num_dirs_to_use]

    print(f"Using {SLICE}% of directories: {num_dirs_to_use}/{len(processed_dirs)} directories")
    print(f"Selected directories: {selected_dirs[0]} to {selected_dirs[-1]}")

    print("Collecting files...")
    unicode_files_paths = []

    for dir_path in selected_dirs:
        if os.path.isdir(dir_path):
            unicode_files_in_dir = [os.path.join(dir_path, f) for f in os.listdir(dir_path) if f.endswith('.txt')]
            unicode_files_paths.extend(unicode_files_in_dir)
            print(f"  - {os.path.basename(dir_path)}: {len(unicode_files_in_dir)} Unicode files")

    if not unicode_files_paths:
        raise FileNotFoundError("No Unicode files found in the specified directory.")

    print(f"Found {len(unicode_files_paths)} Unicode files.")
    print(f"Processing in batches of {BATCH_SIZE} files ...")

    # Process files in batches to avoid memory issues
    with open(output, "w", encoding="utf-8", buffering=8192*8) as outfile:
        print(f"Writing Unicode files to {output}...")

        # Create progress bar for all files
        with tqdm(total=len(unicode_files_paths),
                desc="Processing files",
                unit="file",
                bar_format="{l_bar}{bar}| {n_fmt}/{total_fmt} [{elapsed}<{remaining}, {rate_fmt}]") as pbar:
            
            start_time = time.time()
            
            # Process files in batches
            for i in range(0, len(unicode_files_paths), BATCH_SIZE):
                batch = unicode_files_paths[i:i + BATCH_SIZE]
                batch_num = i // BATCH_SIZE + 1
                total_batches = (len(unicode_files_paths) + BATCH_SIZE - 1) // BATCH_SIZE
                
                tqdm.write(f"Processing batch {batch_num}/{total_batches} ({len(batch)} files)")
                
                # Process this batch
                process_batch(batch, pbar, outfile)
                
                # Update ETA every batch
                elapsed = time.time() - start_time
                files_processed = min(i + BATCH_SIZE, len(unicode_files_paths))
                if files_processed > 0:
                    avg_time_per_file = elapsed / files_processed
                    remaining_files = len(unicode_files_paths) - files_processed
                    eta = avg_time_per_file * remaining_files
                    
                    pbar.set_description(f"Processing files (ETA: {eta:.0f}s)")

    print("Process finished")

if __name__ == "__main__":
    multiprocessing.freeze_support() # For Windows compatibility
    main()
