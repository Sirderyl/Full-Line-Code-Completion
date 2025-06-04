identifiers_file = "./data/analysis_output/identifiers_weighted_count.txt"
vocab_file = "./yttm_models_test/vocab.txt"

with open(identifiers_file, "r", encoding="utf-8") as f:
    lines = [line.strip().split(": ") for line in f]
    identifiers = [ident for ident, _ in lines][:500]

print(f"Loaded {len(identifiers)}...")

vocabulary_tokens = set()
try:
    with open(vocab_file, "r", encoding="utf-8") as f:
        for line in f:
            parts = line.strip().split('\t')
            if len(parts) >= 2:
                token = parts[1]
                vocabulary_tokens.add(token)
except Exception as e:
    print(f"Error: {e}")
    exit()

print(f"Loaded {len(vocabulary_tokens)} tokens from vocabulary file...")

included_count = 0
included_identifiers = []
not_included_identifiers = []

for identifier in identifiers:
    if identifier in vocabulary_tokens:
        included_count += 1
        included_identifiers.append(identifier)
    else:
        not_included_identifiers.append(identifier)

print(f"\nNumber of top {len(identifiers)} included in vocab: {included_count}")
print(f"Percent included: {(included_count / len(identifiers) * 100):.2f}%")