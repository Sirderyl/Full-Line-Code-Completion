import torch
from transformers import AutoTokenizer, AutoModelForCausalLM
import subprocess
import os
import time
import psutil

# --- Configuration ---
CHECKPOINT_PATH = "./model/final"
DEVICE = "cpu"  # Uses CPU for benchmarking for intended use case. Change to "cuda" if you have a GPU and want to use it
JAR_PATH = "./parser/ModelTokenCodec.jar"   # Path to the Java JAR file for encoding/decoding created from ModelTokenCodec
ANTLR_PATH = "./parser/antlr-4.13.2-complete.jar"   # Path to the ANTLR JAR file (needed for encoding/decoding)

print(f"Using device: {DEVICE}")
print(f"Loading model from: {CHECKPOINT_PATH}")

def run_codec(mode, text_input):
    if not os.path.exists(JAR_PATH) or not os.path.exists(ANTLR_PATH):
        raise FileNotFoundError(f"JAR file not found at {JAR_PATH}. Please check the path.")
    
    # os.pathsep automatically uses the correct separator (';' for Windows, ':' for Linux/macOS)
    classpath = f"{JAR_PATH}{os.pathsep}{ANTLR_PATH}"
    command = ["java", "-cp", classpath, "com.codelm.ModelTokenCodec", mode]
    result = subprocess.run(command, input=text_input, capture_output=True, text=True, check=True, encoding='utf-8')

    return result.stdout

# Measure memory before loading model
process = psutil.Process(os.getpid())
memory_before_model = process.memory_info().rss / 1024**3
print(f"\nMemory before loading model: {memory_before_model:.2f} GB")

# --- Load Tokenizer and Model ---
# Use AutoClass to automatically load the correct model and tokenizer type
# from the checkpoint's configuration files.
try:
    tokenizer = AutoTokenizer.from_pretrained(CHECKPOINT_PATH)

    # Measure memory after tokenizer
    memory_after_tokenizer = process.memory_info().rss / 1024**3
    print(f"\nMemory after loading tokenizer: {memory_after_tokenizer:.2f} GB")
    print(f"Tokenizer memory usage: {memory_after_tokenizer - memory_before_model:.2f} GB")

    model = AutoModelForCausalLM.from_pretrained(
        CHECKPOINT_PATH,
        torch_dtype="auto"
    ).to(DEVICE)
    #model.eval()

    # Measure memory after model loading
    memory_after_model = process.memory_info().rss / 1024**3
    print(f"\nMemory after loading model: {memory_after_model:.2f} GB")
    print(f"Model memory usage: {memory_after_model - memory_after_tokenizer:.2f} GB\n")
except Exception as e:
    print(f"Error loading model or tokenizer: {e}")
    print("Please ensure the CHECKPOINT_PATH is correct and contains the necessary files.")
    exit()

print("Model and tokenizer loaded successfully.")
print(f"Model has {model.num_parameters():,} parameters.")
print(f"Tokenizer has {len(tokenizer)} tokens.")

# System memory usage
system_memory_mb = memory_after_model * 1024  # Convert back to MB for display
print(f"\n--- System Memory Usage After Model Loading ---")
print(f"Total Process Memory Usage: {memory_after_model:.2f} GB ({system_memory_mb:.1f} MB)")
print(f"Estimated Model + Tokenizer Memory: {memory_after_model - memory_before_model:.2f} GB\n")

# --- Define input prompts for generation ---

prompt = '''public class HelloWorld {
public static void main(String[] args) {'''

prompt2 = '''package com.codelm;
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
public CumulativeTokenStats() {'''

# Encode the Java string prompt into the Unicode format for the model
try:
    unicode_prompt = run_codec("encode", prompt2)
    print(f"Prompt encoded successfully.\n{unicode_prompt}")
except (subprocess.CalledProcessError, FileNotFoundError) as e:
    print(f"Error running the Java encoder: {e}")
    if isinstance(e, subprocess.CalledProcessError):
        print(f"Java Error Output: \n{e.stderr}")
    exit()

# --- Generate Code ---
# Tokenize the input prompt
inputs = tokenizer(unicode_prompt, return_tensors="pt").to(DEVICE)
input_len = inputs["input_ids"].shape[-1]

print("\n--- Generating code ---")
print(f"Prompt:\n{prompt2}")

# Monitor memory before generation
memory_before_generation = process.memory_info().rss / 1024**3
print(f"\nMemory before generation: {memory_before_generation:.2f} GB")

# Generate text using beam search
start = time.perf_counter()

with torch.no_grad(): # Disable gradient calculations for inference
    outputs = model.generate(
        input_ids=inputs["input_ids"],
        attention_mask=inputs["attention_mask"],
        max_new_tokens=50,          # Maximum number of new tokens to generate
        num_beams=4,
        num_return_sequences=4,
        pad_token_id=tokenizer.pad_token_id
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
print(f"Total generated tokens: {total_len}")
new_tokens = total_len - input_len
duration = end - start
toks_per_sec = new_tokens / duration

print("\n--- Final Generated Java Code ---")

# Get the full generated output in Unicode format
eol_token = ""
unicode_output = tokenizer.decode(outputs[0], skip_special_tokens=True, clean_up_tokenization_spaces=False)
unicode_output = unicode_output.replace(' ', '')
# This line cuts off the rest of the output after the first occurrence of the end-of-line token. Comment out to keep multi-line generated code.
unicode_output = unicode_output.split(eol_token, 1)[0] + eol_token

# Decode the full Unicode output back into readable Java code
try:
    generated_java_code = run_codec("decode", unicode_output)
    print(generated_java_code)
except (subprocess.CalledProcessError, FileNotFoundError) as e:
    print(f"Error running the Java decoder: {e}")
    if isinstance(e, subprocess.CalledProcessError):
        print(f"Java Error Output: \n{e.stderr}")
    exit()

print(f"Generated {new_tokens} tokens in {duration:.3f}s → {toks_per_sec:.1f} tokens/s")
