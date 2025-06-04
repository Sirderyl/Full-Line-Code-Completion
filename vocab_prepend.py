identifiers_file = "./data/analysis_output/identifiers_weighted_count.txt"

with open(identifiers_file, "r", encoding="utf-8") as f:
    lines = [line.strip().split(": ") for line in f]
    identifiers = [(ident, int(weight)) for ident, weight in lines]

top_500_identifiers = [ident for ident, _ in identifiers[:500]]

with open('./bpe_models_test/bpe_model_k_0.vocab', 'r', encoding='utf-8') as f:
    vocab_content = f.read()
    vocab_lines = vocab_content.splitlines()

vocab_dict = {}
for line in vocab_lines:
    if line.strip():
        parts = line.split('\t')
        if len(parts) == 2:
            word, id_val = parts
            vocab_dict[word] = id_val

# Find identifiers not already in the file
to_prepend = []
for identifier in top_500_identifiers:
    if identifier not in vocab_dict:
        to_prepend.append(identifier)

# Prepend the missing identifiers
if to_prepend:
    num_new_entries = len(to_prepend)

    special_tokens = []
    regular_tokens = []
    for word, id_val in vocab_dict.items():
        if id_val == '0':
            special_tokens.append((word, id_val))
        else:
            regular_tokens.append((word, id_val))

    new_entries = [(identifier, f"-{i}") for i, identifier in enumerate(to_prepend)]

    shifted_regular_tokens = []
    for word, id_val in regular_tokens:
        try:
            # Parse ID, shift it by number of new entries
            id_num = int(id_val)
            new_id = id_num - num_new_entries
            shifted_regular_tokens.append((word, f"{new_id}"))
        except ValueError:
            # If ID is not an integer, keep it as is
            shifted_regular_tokens.append((word, id_val))

    with open('./bpe_models_test/bpe_model_k_0.vocab', 'w', encoding="utf-8") as f:
        # Write the special tokens first
        for word, id_val in special_tokens:
            f.write(f"{word}\t{id_val}\n")

        # Write the new entries
        for word, id_val in new_entries:
            f.write(f"{word}\t{id_val}\n")

        # Write the shifted regular tokens
        for word, id_val in shifted_regular_tokens:
            f.write(f"{word}\t{id_val}\n")

    print(f"Added {len(to_prepend)} new identifiers to the vocabulary file")
else:
    print("All identifiers already exist in the vocabulary file")
