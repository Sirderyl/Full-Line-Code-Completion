import os
from tqdm import tqdm
import time

file_dir = "./data/clean_extract/processed_part_0001"
output = "./data/clean_extract/corpus6.txt"

print("Collecting Java files...")
java_files_paths = [os.path.join(file_dir, f) for f in os.listdir(file_dir) if f.endswith('.java')]

if not java_files_paths:
    raise FileNotFoundError("No Java files found in the specified directory.")

print(f"Found {len(java_files_paths)} Java files.")

with open(output, "w", encoding="utf-8") as outfile:
    print(f"Writing Java files to {output}...")
    
    # Create progress bar with file count and estimated time
    with tqdm(total=len(java_files_paths),
              desc="Processing files",
              unit="file",
              bar_format="{l_bar}{bar}| {n_fmt}/{total_fmt} [{elapsed}<{remaining}, {rate_fmt}]") as pbar:
        
        start_time = time.time()
        files_processed = 0
        
        for java_file in java_files_paths:
            try:
                with open(java_file, 'r', encoding='utf-8') as f_java:
                    lines = f_java.readlines()

                    # Strip leading and trailing whitespace and remove empty lines
                    processed_lines = []
                    for line in lines:
                        stripped_line = line.strip()
                        if stripped_line:
                            #stripped_line = stripped_line.replace(" ", "\u200B") # Remove all spaces
                            processed_lines.append(stripped_line)
                    
                    if processed_lines:
                        content = "\n".join(processed_lines)
                        outfile.write(content)
                        outfile.write("\n")
                        
            except Exception as e:
                tqdm.write(f"Error reading {java_file}: {e}")
            
            # Update progress bar
            pbar.update(1)
            files_processed += 1
            
            # Update description with additional stats every 100 files
            if files_processed % 100 == 0:
                elapsed = time.time() - start_time
                avg_time_per_file = elapsed / files_processed
                remaining_files = len(java_files_paths) - files_processed
                eta = avg_time_per_file * remaining_files
                
                pbar.set_description(f"Processing files (ETA: {eta:.0f}s)")

print("Process finished")
