import os
import youtokentome as yttm

corpus = "./data/clean_extract/corpus2.txt"
#bpe_path = "./yttm_models_test/yttm_vocab_16384_nospace.bpe"
jb_bpe_path = "./JetBrains_model/flcc.bpe"

# Load the trained model
#bpe = yttm.BPE(model=bpe_path)
jb_bpe = yttm.BPE(model=jb_bpe_path)

# Tokenize the dataset and count tokens
print("Tokenizing corpus and counting tokens...")
#total_tokens = 0
jb_total_tokens = 0

with open(corpus, "r", encoding="utf-8") as f:
    content = f.read()
    #tokens = bpe.encode([content], output_type=yttm.OutputType.ID)[0]
    jb_tokens = jb_bpe.encode([content], output_type=yttm.OutputType.ID)[0]
    #total_tokens += len(tokens)
    jb_total_tokens += len(jb_tokens)

#print(f"Total tokens in all Java files: {total_tokens}")
print(f"Total tokens using JetBrains model: {jb_total_tokens}")