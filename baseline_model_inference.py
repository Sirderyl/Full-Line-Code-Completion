import os
import youtokentome as yttm
from transformers import LlamaForCausalLM
import torch
import time
import psutil

DEVICE = "cpu"

# Measure memory before loading model
process = psutil.Process(os.getpid())
memory_before_model = process.memory_info().rss / 1024**3
print(f"\nMemory before loading model: {memory_before_model:.2f} GB")

bpe = yttm.BPE(model="JetBrains_model/flcc.bpe")

# Measure memory after tokenizer
memory_after_tokenizer = process.memory_info().rss / 1024**3
print(f"\nMemory after loading tokenizer: {memory_after_tokenizer:.2f} GB")
print(f"Tokenizer memory usage: {memory_after_tokenizer - memory_before_model:.2f} GB")

model = LlamaForCausalLM.from_pretrained("JetBrains_model", gguf_file="flcc.model", torch_dtype=torch.float32).to(DEVICE)

# Measure memory after model loading
memory_after_model = process.memory_info().rss / 1024**3
print(f"\nMemory after loading model: {memory_after_model:.2f} GB")
print(f"Model memory usage: {memory_after_model - memory_after_tokenizer:.2f} GB\n")

print(model.num_parameters())

prefix_code = """
public class HelloWorld {
    public static void main(String[] args) {
        System.out.
"""

prefix_code2 = """package com.codelm;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CumulativeTokenStats {

    private long totalTokens;
    private Map<String, Long> tokenTypeCounts;
    private long totalLiteralChars;
    private int maxLiteralChars;
    private long totalStringLiterals;
    private long totalIdentifierChars;
    private int maxIdentifierChars;
    private long totalBytes;
    private long totalLiteralBytes;
    private long totalIdentifierBytes;

    public CumulativeTokenStats() {"""

# System memory usage
system_memory_mb = memory_after_model * 1024  # Convert back to MB for display
print(f"\n--- System Memory Usage After Model Loading ---")
print(f"Total Process Memory Usage: {memory_after_model:.2f} GB ({system_memory_mb:.1f} MB)")
print(f"Estimated Model + Tokenizer Memory: {memory_after_model - memory_before_model:.2f} GB\n")

tokens = bpe.encode(prefix_code, output_type=yttm.OutputType.ID)

# Print tokenization to verify
print(f"Token IDs: {tokens}")
print(f"Decoded back: {bpe.decode([tokens])[0]}")

# Convert to tensor
input_ids = torch.tensor([tokens]).to(model.device)
attention_mask = torch.ones_like(input_ids).to(model.device)
input_len = input_ids.shape[-1]

# Monitor memory before generation
memory_before_generation = process.memory_info().rss / 1024**3
print(f"\nMemory before generation: {memory_before_generation:.2f} GB")

# Generate completions
start = time.perf_counter()

with torch.no_grad():
    outputs = model.generate(
        input_ids,
        attention_mask=attention_mask,
        max_new_tokens=50,  # Controls how many new tokens to generate
        num_beams=4,
        num_return_sequences=4,
        pad_token_id=model.config.eos_token_id,  # Set padding token to EOS token
        #repetition_penalty=1.2
    )

end = time.perf_counter()

# Final system memory check
final_system_memory_gb = process.memory_info().rss / 1024**3
print(f"\nMemory after generation: {final_system_memory_gb:.2f} GB")
print(f"Generation memory overhead: {final_system_memory_gb - memory_before_generation:.2f} GB")
print(f"Total memory increase from start: {final_system_memory_gb - memory_before_model:.2f} GB")

# Count how many new tokens were generated and tokens/sec
generated_ids = outputs[0]
total_len = generated_ids.shape[-1]
new_tokens = total_len - input_len
duration = end - start
toks_per_sec = new_tokens / duration

# Print the raw output to understand what's happening
print("Raw output tokens:", outputs[0].tolist())

# Get the generated content after the prompt
original_length = len(tokens)
generated_tokens = outputs[0][original_length:].tolist()

# Decode only the newly generated tokens
completion = bpe.decode([generated_tokens])[0]

# Format the output
print("Input prompt:")
print(prefix_code.strip())
print("\nGenerated completion:")
print(f"{completion}")

print(f"Generated {new_tokens} tokens in {duration:.3f}s → {toks_per_sec:.1f} tokens/s")