import pathlib
from tokenizers import Tokenizer, normalizers
from tokenizers.models import BPE
from tokenizers.trainers import BpeTrainer
from tokenizers.pre_tokenizers import Whitespace

ID_CORPUS = "./data/clean_extract/corpus2_ids.txt"
UNICODE_CORPUS = "./data/clean_extract/corpus2_unicode.txt"
SAVE_PATH = "./custom_bpe_0/bpe_tokenizer.json"
OUTPUT_ASSET_DIR = pathlib.Path("./custom_bpe_0/assets")

INITIAL_VOCAB_SIZE = 226
TARGET_VOCAB_SIZE = 16384

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

def corpus_iterator(unicode_corpus_path):
    with open(unicode_corpus_path, "r", encoding="utf-8") as f:
        for line_num, line in enumerate(f):
            line = line.strip()
            yield line

def train_bpe():
    OUTPUT_ASSET_DIR.mkdir(parents=True, exist_ok=True)

    # The initial vocabulary of predefined ID tokens
    initial_alphabet = list(id_to_char.values())

    # Special tokens for BPE, such as unknown
    special_tokens = ["<UNK>"]

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

    train_corpus = [UNICODE_CORPUS]

    print("Starting BPE training...")
    #tokenizer.train(train_corpus, trainer=trainer)
    tokenizer.train_from_iterator(
        corpus_iterator(UNICODE_CORPUS),
        trainer=trainer
    )

    tokenizer.save(SAVE_PATH)
    print(f"\nTokenizer saved to: {SAVE_PATH}")

    # Save the vocabulary
    bpe_vocab_path = OUTPUT_ASSET_DIR / "vocab.txt"
    save_vocab(tokenizer, bpe_vocab_path)
    print(f"Vocabulary saved to: {bpe_vocab_path}")
    print(f"Final vocabulary size: {tokenizer.get_vocab_size()}")

def count_tokens(corpus_path: str, tokenizer_path: str):
    print(f"Loading tokenizer from: {tokenizer_path}")
    tokenizer = Tokenizer.from_file(tokenizer_path)

    total_tokens = 0
    processed_lines = 0

    print(f"Processing ID corpus: {corpus_path} to count BPE tokens.")

    with open(corpus_path, "r", encoding="utf-8") as f_corpus:
        for _, line_content in enumerate(f_corpus):
            id_tokens = line_content.strip().split()

            # Convert ID tokens in the line to their corresponding Unicode characters.
            # This mimics the structure of UNICODE_CORPUS used for training.
            unicode_string_to_encode = ''.join([id_to_char[t] for t in id_tokens])

            encoding = tokenizer.encode(unicode_string_to_encode)
            total_tokens += len(encoding.tokens)

            processed_lines += 1
            if processed_lines > 0 and processed_lines % 100_000 == 0: # Progress indicator
                print(f"Processed {processed_lines} lines, total BPE tokens so far: {total_tokens}")

    print(f"\nFinished processing {processed_lines} lines from '{corpus_path}'.")
    print(f"Total BPE tokens generated: {total_tokens}")
    return total_tokens

if __name__ == "__main__":
    convert_id_to_unicode(ID_CORPUS, UNICODE_CORPUS)
    train_bpe()
    count_tokens(ID_CORPUS, SAVE_PATH)
