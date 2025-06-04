import os

file_dir = "./data/clean_extract/processed_part_0000"
output = "./data/clean_extract/corpus3.txt"

print("Collecting Java files...")
java_files_paths = [os.path.join(file_dir, f) for f in os.listdir(file_dir) if f.endswith('.java')]

if not java_files_paths:
    raise FileNotFoundError("No Java files found in the specified directory.")

print(f"Found {len(java_files_paths)} Java files.")

with open(output, "w", encoding="utf-8") as outfile:
    print(f"Writing Java files to {output}...")
    for java_file in java_files_paths:
        try:
            with open(java_file, 'r', encoding='utf-8') as f_java:
                lines = f_java.readlines()

                # Strip leading and traling whitespace and remove empty lines
                processed_lines = []
                for line in lines:
                    stripped_line = line.strip()
                    if stripped_line:
                        words = stripped_line.split()
                        stripped_line = stripped_line.replace(" ", "") # Remove all spaces
                        processed_lines.append(stripped_line)
                
                if processed_lines:
                    content = "\n".join(processed_lines)
                    outfile.write(content)
                    outfile.write("\n")
        except Exception as e:
            print(f"Error reading {java_file}: {e}")
