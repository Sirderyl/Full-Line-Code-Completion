import pathlib
import glob
import os
from tokenizers import Tokenizer, normalizers
from tokenizers.models import BPE
from tokenizers.trainers import BpeTrainer
from tokenizers.pre_tokenizers import Whitespace

ID_CORPUS = "./data/corpus_ids.txt"                             # Path to the ID corpus (no need to use this if corpus is already converted from ModelTokenCodec)
UNICODE_CORPUS = "/data/corpus_unicode.txt"                     # Path to the Unicode corpus file
ID_TO_TOKEN_FILE = "./data/processed_dataset/mapVocab.txt"      # Path to the ID to token mapping file (from ModelTokenCodec)
SAVE_PATH = "./custom_bpe_final/bpe_tokenizer.json"             # Path to save the trained BPE tokenizer
OUTPUT_ASSET_DIR = pathlib.Path("./custom_bpe_final/assets")    # Directory to save the assets like vocab.txt and vocab_converted.txt

INITIAL_VOCAB_SIZE = 226    # Initial reserved vocabulary size based on ModelTokenCodec's ID tokens (0-225)
TARGET_VOCAB_SIZE = 16384   # Target vocabulary size for BPE

# Map "0" -> U+E000 ... "225" -> U+E0E1
id_to_char = { str(i): chr(0xE000 + i) for i in range(INITIAL_VOCAB_SIZE) }
char_to_id = { v: k for k, v in id_to_char.items() }

def convert_id_to_unicode(id_corpus_path, unicode_corpus_path):
    with open(id_corpus_path, "r", encoding="utf-8") as in_f, \
        open(unicode_corpus_path, "w", encoding="utf-8") as out_f:
        for line in in_f:
            tokens = line.strip().split()
            if not tokens:
                continue
            chars = [id_to_char[t] for t in tokens]
            out_f.write(''.join(chars) + '\n')

def load_id_to_token_mapping(id_vocab_file):
    id_to_token = {}
    with open(id_vocab_file, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            parts = line.split(' ', 2)
            if len(parts) == 3:
                token_id = parts[0]
                token_text = parts[2]
                id_to_token[token_id] = token_text
            else:
                token_id = parts[0]
                id_to_token[token_id] = ""
    return id_to_token

def convert_ids_to_text(id_sequence, ids_to_tokens):
    readable_tokens = []
    for token_id in id_sequence:
        if str(token_id) in ids_to_tokens:
            token_text = ids_to_tokens[token_id]
            if "IDENTIFIER" not in token_text:
                readable_tokens.append(ids_to_tokens[token_id])
        else:
            readable_tokens.append(f"UNK_ID_{token_id}")
    return readable_tokens

def save_vocab(tokenizer, vocab_path):
    bpe_vocab = tokenizer.get_vocab()

    with open(vocab_path, "w", encoding="utf-8") as f:
        for token_str, bpe_id in sorted(bpe_vocab.items(), key=lambda item: item[1]):
            readable_parts = []
            is_special = False

            for char in token_str:
                # If a character is not in map, it's a special token (<UNK>)
                if char not in char_to_id:
                    is_special = True
                    break
                readable_parts.append(char_to_id[char])

            if is_special:
                readable_token = token_str
            else:
                readable_token = ' '.join(readable_parts)

            f.write(f"{readable_token}\t{bpe_id}\n")

def save_vocab_with_readable_merges(tokenizer, vocab_path, id_to_token_file):
    # Load the ID to token mapping
    ids_to_tokens = load_id_to_token_mapping(id_to_token_file)
    
    bpe_vocab = tokenizer.get_vocab()

    with open(vocab_path, "w", encoding="utf-8") as f:
        for token_str, bpe_id in sorted(bpe_vocab.items(), key=lambda item: item[1]):
            is_special = False

            # Check if this is a special token
            for char in token_str:
                if char not in char_to_id:
                    is_special = True
                    break

            if is_special:
                # This is a special token like <UNK>
                f.write(f"{token_str}\t{bpe_id}\n")
                continue
            else:
                # Convert Unicode characters back to IDs, then to readable tokens
                id_sequence = []
                for char in token_str:
                    original_id = char_to_id[char]
                    id_sequence.append(original_id)
                
                # Convert IDs to readable tokens
                readable_tokens = convert_ids_to_text(id_sequence, ids_to_tokens)
                
                # Format the output
                if len(readable_tokens) == 1:
                    # Single token
                    merge_representation = readable_tokens[0]
                    resulting_word = readable_tokens[0]
                else:
                    # BPE merge - show the sequence
                    merge_representation = " + ".join(readable_tokens)
                    resulting_word = "".join(readable_tokens)

            if len(readable_tokens) == 1:
                f.write(f"{merge_representation}\t{bpe_id}\n")
            else:
                f.write(f"{merge_representation} -> \"{resulting_word}\"\t{bpe_id}\n")

def convert_unicode_to_ids(unicode_corpus_path, id_corpus_path):
    with open(unicode_corpus_path, "r", encoding="utf-8", newline="\n") as in_f, \
        open(id_corpus_path, "w", encoding="utf-8", newline="\n") as out_f:
        for line in in_f:
            tokens = line.strip()
            if not tokens:
                continue
            id_tokens = [char_to_id[char] for char in tokens if char in char_to_id]
            out_f.write(' '.join(id_tokens) + '\n')

def corpus_iterator(unicode_corpus_path):
    with open(unicode_corpus_path, "r", encoding="utf-8") as f:
        for _, line in enumerate(f):
            line = line.strip()
            yield line

def train_bpe():
    OUTPUT_ASSET_DIR.mkdir(parents=True, exist_ok=True)

    # The initial vocabulary of predefined ID tokens
    initial_alphabet = list(id_to_char.values())

    # Special tokens for BPE, such as unknown
    special_tokens = ["<UNK>", "<PAD>"]

    tokenizer = Tokenizer(BPE(unk_token=special_tokens[0]))

    # Split the IDs by whitespace
    tokenizer.pre_tokenizer = None
    tokenizer.normalizer = normalizers.NFC()

    trainer = BpeTrainer(
        vocab_size=TARGET_VOCAB_SIZE,
        initial_alphabet=initial_alphabet,
        special_tokens=special_tokens,
        min_frequency=2,    # Minimum frequency for a pair to be merged
        show_progress=True
    )

    print(f"Target vocabulary size: {TARGET_VOCAB_SIZE}")
    print(f"Number of initial ID tokens: {len(initial_alphabet)}")

    print("Starting BPE training...")

    tokenizer.train_from_iterator(
        corpus_iterator(UNICODE_CORPUS),
        trainer=trainer
    )

    tokenizer.save(SAVE_PATH)
    print(f"\nTokenizer saved to: {SAVE_PATH}")

    # Save the vocabulary
    bpe_vocab_path = OUTPUT_ASSET_DIR / "vocab.txt"
    bpe_vocab_path_converted = OUTPUT_ASSET_DIR / "vocab_converted.txt"
    save_vocab(tokenizer, bpe_vocab_path)
    save_vocab_with_readable_merges(tokenizer, bpe_vocab_path_converted, ID_TO_TOKEN_FILE)
    print(f"Vocabulary saved to: {bpe_vocab_path}")
    print(f"Final vocabulary size: {tokenizer.get_vocab_size()}")

def count_tokens(corpus_path: str, tokenizer_path: str):
    print(f"Loading tokenizer from: {tokenizer_path}")
    tokenizer = Tokenizer.from_file(tokenizer_path)

    total_tokens = 0
    processed_lines = 0

    print(f"Processing corpus: {corpus_path} to count BPE tokens.")

    with open(corpus_path, "r", encoding="utf-8") as f_corpus:
        for _, line_content in enumerate(f_corpus):
            id_tokens = line_content.strip()

            if not line_content:    # Skip empty lines
                continue

            # Uncomment if the corpus passed is in ID format

            # Convert ID tokens in the line to their corresponding Unicode characters.
            # This mimics the structure of UNICODE_CORPUS used for training.
            #unicode_string_to_encode = ''.join([id_to_char[t] for t in id_tokens])

            encoding = tokenizer.encode(id_tokens)
            total_tokens += len(encoding.tokens)

            processed_lines += 1
            if processed_lines > 0 and processed_lines % 100_000 == 0: # Progress indicator
                avg_tokens_per_line = total_tokens / processed_lines
                print(f"Processed {processed_lines} lines, total BPE tokens so far: {total_tokens}, "
                      f"avg tokens/line: {avg_tokens_per_line:.2f}")

    avg_tokens_per_line = total_tokens / processed_lines if processed_lines > 0 else 0

    print(f"\nFinished processing {processed_lines} lines from '{corpus_path}'.")
    print(f"Total BPE tokens generated: {total_tokens}")
    print(f"Average tokens per line: {avg_tokens_per_line:.2f}")
    return total_tokens

def count_tokens_from_subfolders(base_path: str, tokenizer_path: str):
    print(f"Loading tokenizer from: {tokenizer_path}")
    tokenizer = Tokenizer.from_file(tokenizer_path)

    total_tokens = 0
    processed_lines = 0
    processed_files = 0

    print(f"Processing multi-part corpus from: {base_path}")

    # Find all processed_part_* directories
    part_dirs = glob.glob(os.path.join(base_path, "processed_part_*"))
    part_dirs.sort()  # Ensure consistent ordering
    
    print(f"Found {len(part_dirs)} part directories")

    for part_dir in part_dirs:
        print(f"Processing directory: {os.path.basename(part_dir)}")
        
        # Find all .txt files in this part directory
        txt_files = glob.glob(os.path.join(part_dir, "*.txt"))
        
        for file_path in txt_files:
            #print(f"  Processing file: {os.path.basename(file_path)}")
            
            with open(file_path, "r", encoding="utf-8") as f_corpus:
                for _, line_content in enumerate(f_corpus):
                    tokens = line_content.strip()
                    
                    if not tokens:  # Skip empty lines
                        continue

                    encoding = tokenizer.encode(tokens)
                    total_tokens += len(encoding.tokens)

                    processed_lines += 1
                    if processed_lines > 0 and processed_lines % 100_000 == 0: # Progress indicator
                        avg_tokens_per_line = total_tokens / processed_lines
                        print(f"    Processed {processed_lines} lines, total BPE tokens so far: {total_tokens}, "
                              f"avg tokens/line: {avg_tokens_per_line:.2f}")
            
            processed_files += 1

    avg_tokens_per_line = total_tokens / processed_lines if processed_lines > 0 else 0
    
    print(f"\nFinished processing {processed_files} files across {len(part_dirs)} directories.")
    print(f"Total lines processed: {processed_lines}")
    print(f"Total BPE tokens generated: {total_tokens}")
    print(f"Average tokens per line: {avg_tokens_per_line:.2f}")
    return total_tokens

if __name__ == "__main__":
    # Uncomment if needed to convert ID corpus to Unicode
    #convert_id_to_unicode(ID_CORPUS, UNICODE_CORPUS)
    train_bpe()
    count_tokens(UNICODE_CORPUS, SAVE_PATH)
    bpe_vocab_path_converted = OUTPUT_ASSET_DIR / "vocab_converted.txt"
    tokenizer = Tokenizer.from_file(SAVE_PATH)
    save_vocab_with_readable_merges(tokenizer, bpe_vocab_path_converted, ID_TO_TOKEN_FILE)
