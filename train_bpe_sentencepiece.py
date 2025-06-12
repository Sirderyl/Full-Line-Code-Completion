import pathlib as path
import zipfile
import tempfile
import os
import sentencepiece as spm
import matplotlib.pyplot as plt

file_dir = "./data/clean_extract/processed_part_0000"
identifiers_file = "./data/analysis_output/identifiers_weighted_count.txt"
output_dir = path.Path("bpe_models_test2")
output_dir.mkdir(exist_ok=True)

vocab_size = 16000

with open(identifiers_file, "r", encoding="utf-8") as f:
    lines = [line.strip().split(": ") for line in f]
    identifiers = [(ident, int(weight)) for ident, weight in lines]

# Identifier vocab sizes to test
k_values = [500]
token_counts = []

'''
# Create a temporary directory to extract Java files
with tempfile.TemporaryDirectory() as temp_dir:
    # Extract 10% of the zip files
    zip_files = [os.path.join(zip_dir, f) for f in os.listdir(zip_dir) if f.endswith('.zip')]
    zip_files_subset = zip_files[:1]
    print("Extracting zip files...")
    for zip_file in zip_files_subset:
        with zipfile.ZipFile(zip_file, 'r') as zf:
            print(f"Extracting {zip_file}...")
            zf.extractall(temp_dir)
'''
    
# Collect all extracted Java files
java_files = [os.path.join(file_dir, f) for f in os.listdir(file_dir) if f.endswith('.java')]

for k in k_values:
    print(f"Training with K = {k}")
    top_k_identifiers = [ident for ident, _ in identifiers[:k]]

    # Train the SentencePiece model
    model_prefix = output_dir / f"bpe_model_k_{k}"
    spm.SentencePieceTrainer.Train(
        input=java_files,
        model_prefix=str(model_prefix),
        vocab_size=vocab_size,
        user_defined_symbols=top_k_identifiers,
        model_type="bpe",
        #character_coverage=1.0
    )

    # Load the trained model
    sp = spm.SentencePieceProcessor()
    sp.Load(f"{model_prefix}.model")

    # Tokenize the dataset and count tokens
    total_tokens = 0
    for java_file in java_files:
        with open(java_file, "r", encoding="utf-8") as f:
            content = f.read()
            tokens = sp.Encode(content, out_type=int)
            total_tokens += len(tokens)

    token_counts.append(total_tokens)
    print(f"K = {k}, Total tokens: {total_tokens}")

'''
# Plot results
plt.plot(k_values, token_counts, marker='o')
plt.xlabel("Number of Reserved Identifiers (K)")
plt.ylabel("Total Token Count")
plt.title("Token Count vs. Reserved Identifier Size")
plt.grid(True)
plt.savefig("token_count_vs_k.png")
print("Plot saved as 'token_count_vs_k.png'")
'''
