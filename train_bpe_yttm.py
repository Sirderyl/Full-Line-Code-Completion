import pathlib as path
import zipfile
import tempfile
import os
import youtokentome as yttm
import matplotlib.pyplot as plt

corpus = "./data/clean_extract/corpus3.txt"
#identifiers_file = "./data/analysis_output/identifiers_weighted_count.txt"

'''
with open(identifiers_file, "r", encoding="utf-8") as f:
    lines = [line.strip().split(": ") for line in f]
    identifiers = [ident for ident, _ in lines][:500]
'''

output_dir = path.Path("yttm_models_test")
output_dir.mkdir(exist_ok=True)

vocab_size = 16384

# Train the YTTM model
model_output_path = str(output_dir / f"yttm_vocab_{vocab_size}_nospace.bpe")
print(f"Training YTTM model with vocab size {vocab_size}...")

yttm.BPE.train(
    data=corpus,
    model=model_output_path,
    vocab_size=vocab_size,
    coverage=0.9999
)

print(f"YTTM model trained and saved to {model_output_path}")