import sentencepiece as spm
import os

path = "./bpe_models/bpe_model_k_0.model"

sp = spm.SentencePieceProcessor()
sp.Load(path)

file_dir = "./data/clean_extract/processed_part_0000"

# Collect all extracted Java files
java_files = [os.path.join(file_dir, f) for f in os.listdir(file_dir) if f.endswith('.java')]

total_tokens = 0
for java_file in java_files:
    with open(java_file, "r", encoding="utf-8") as f:
        content = f.read()
        tokens = sp.Encode(content, out_type=int)
        total_tokens += len(tokens)

print(f"Total tokens: {total_tokens}")
