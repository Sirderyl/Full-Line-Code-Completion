from datasets import Dataset, IterableDataset
import os
import glob
import json
import tarfile
from transformers import PreTrainedTokenizerFast
from transformers import LlamaConfig, LlamaForCausalLM
from transformers import DataCollatorForLanguageModeling
from transformers import TrainingArguments
from transformers import Trainer

def create_train_dataset_generator(base_dir, split_ratio=0.9):
    """Generator that yields Java file contents from multiple folders"""
    # Find all processed_part_xxxx directories
    processed_dirs = glob.glob(os.path.join(base_dir, "processed_part_*"))
    processed_dirs.sort()

    # Split directories for train/val
    split_idx = int(len(processed_dirs) * split_ratio)
    print(f"\nTotal processed directories: {len(processed_dirs)}")
    train_dirs = processed_dirs[:split_idx]
    
    for dir_path in train_dirs:
        if os.path.isdir(dir_path):
            java_files = [os.path.join(dir_path, f) for f in os.listdir(dir_path) if f.endswith('.txt')]
            
            for java_file in java_files:
                try:
                    with open(java_file, 'r', encoding='utf-8') as f:
                        content = f.read().strip()
                        if content:  # Only yield non-empty files
                            yield {"content": content}
                except Exception as e:
                    print(f"Error reading {java_file}: {e}")
                    continue

def create_eval_dataset_generator(base_dir, split_ratio=0.9):
    """Generator for validation data"""
    processed_dirs = glob.glob(os.path.join(base_dir, "processed_part_*"))
    processed_dirs.sort()
    
    # Split directories for train/val
    split_idx = int(len(processed_dirs) * split_ratio)
    eval_dirs = processed_dirs[split_idx:]
    
    for dir_path in eval_dirs:
        if os.path.isdir(dir_path):
            java_files = [os.path.join(dir_path, f) for f in os.listdir(dir_path) if f.endswith('.txt')]
            
            for java_file in java_files:
                try:
                    with open(java_file, 'r', encoding='utf-8') as f:
                        content = f.read().strip()
                        if content:
                            yield {"content": content}
                except Exception as e:
                    print(f"Error reading {java_file}: {e}")
                    continue

def count_train_files(base_dir, split_ratio=0.9):
    """Count total files in training dataset"""
    processed_dirs = glob.glob(os.path.join(base_dir, "processed_part_*"))
    processed_dirs.sort()
    
    # Split directories for train/val
    split_idx = int(len(processed_dirs) * split_ratio)
    train_dirs = processed_dirs[:split_idx]
    
    total_files = 0
    i = 0
    for dir_path in train_dirs:
        if os.path.isdir(dir_path):
            java_files = [f for f in os.listdir(dir_path) if f.endswith('.txt')]
            total_files += len(java_files)
            i += 1
            if i % 100 == 0:
                print(f"Processed directory {i}/{len(train_dirs)}")
    
    return total_files

# Replace the dataset loading lines with:
base_dir = "./data/processed_dataset"

total_train_files = count_train_files(base_dir=base_dir, split_ratio=0.999)

train_streamed_ds = IterableDataset.from_generator(
    lambda: create_train_dataset_generator(base_dir, split_ratio=0.999)
)

eval_streamed_ds = IterableDataset.from_generator(
    lambda: create_eval_dataset_generator(base_dir, split_ratio=0.999)
)

print("Dataset loaded")

tokenizer = PreTrainedTokenizerFast(tokenizer_file="./custom_bpe_final/bpe_tokenizer.json")

tokenizer.unk_token = "<UNK>"
tokenizer.pad_token = "<PAD>"
tokenizer.eos_token_id = 2  # Position of EOS token in the vocabulary
tokenizer.bos_token = None

print(f"Tokenizer loaded with vocab size: {tokenizer.vocab_size}")
print(f"UNK token ID: {tokenizer.unk_token} -> ID: {tokenizer.unk_token_id}")
print(f"PAD token ID: {tokenizer.pad_token} -> ID: {tokenizer.pad_token_id}")
print(f"EOS token ID: {tokenizer.eos_token} -> ID: {tokenizer.eos_token_id}")

print("Loading model config")
# Load the model configuration from JB flcc model
with open("./baseline_model/flcc.json", "r") as f:
    config_dict = json.load(f)

config = LlamaConfig(**config_dict)

config.vocab_size = 16384    # Set vocab size to match tokenizer
print(f"Model config loaded: {config.hidden_size}d, {config.num_hidden_layers} layers, {config.num_attention_heads} heads")

model = LlamaForCausalLM(config)

print(f"Model created with {model.num_parameters():,} parameters")

n_params = model.num_parameters()
print(f"Vocab size {config.vocab_size:,} → {n_params:,} total parameters "
      f"({n_params/1e6:.2f} M)")

# Test the dataset and tokenizer
example = next(iter(train_streamed_ds))
print("Dataset keys:", example.keys())
print("Example content length:", len(example["content"]))
print("Example (first 10 chars):", example["content"][:10])

def tokenize_function(examples):
    return tokenizer(
        examples["content"],
        truncation=True,
        max_length=config.max_position_embeddings,
        padding="max_length"
    )

# Test tokenization
print("Testing tokenization...")
test_encoding = tokenizer(example["content"][:100])
print(f"Test tokens: {len(test_encoding['input_ids'])} tokens")

train_tokenized = train_streamed_ds.map(tokenize_function, batched=True, remove_columns=["content"])
eval_tokenized = eval_streamed_ds.map(tokenize_function, batched=True, remove_columns=["content"])

data_collator = DataCollatorForLanguageModeling(
    tokenizer=tokenizer,
    mlm=False
)

# These values can be adjusted based on available GPU memory. Too high batch size can also lead to overfitting.
per_device_batch_size = 8
gradient_accumulation_steps = 4
effective_batch_size = per_device_batch_size * gradient_accumulation_steps # * num_gpus
max_steps = total_train_files // effective_batch_size

training_args = TrainingArguments(
    output_dir="./model",
    per_device_train_batch_size=per_device_batch_size,
    per_device_eval_batch_size=per_device_batch_size,
    fp16=True,

    logging_steps=100,
    logging_strategy="steps",
    logging_first_step=True,
    report_to=["tensorboard"],
    run_name="java_model_training",

    save_steps=5000,
    save_strategy="steps",
    save_total_limit=5,

    eval_steps=5000,
    eval_strategy="steps",

    learning_rate=1e-4,
    warmup_steps=1000,   # Add warmup for stability
    weight_decay=0.01,   # Add weight decay for regularization
    gradient_accumulation_steps=gradient_accumulation_steps,
    max_steps=max_steps,

    dataloader_drop_last=False,
    dataloader_num_workers=0,

    resume_from_checkpoint=None,

    load_best_model_at_end=True,
    metric_for_best_model="eval_loss",
    greater_is_better=False,

    gradient_checkpointing=True,
    dataloader_pin_memory=True,
)

# Calculate the number of steps for 1 epoch
#total_samples = len(ds)
#effective_batch_size = training_args.per_device_train_batch_size * training_args.gradient_accumulation_steps
#steps_per_epoch = total_samples // effective_batch_size

#print(f"Total samples: {total_samples}")
#print(f"Effective batch size: {effective_batch_size}")
#print(f"Steps per epoch: {steps_per_epoch}")

print("Training arguments configured")
print(f"Effective batch size: {training_args.per_device_train_batch_size * training_args.gradient_accumulation_steps}")
print(f"Total training steps: {training_args.max_steps}")
print(f"Checkpoints will be saved every {training_args.save_steps} steps")
print(f"Total training files: {total_train_files}")

trainer = Trainer(
    model=model,
    args=training_args,
    train_dataset=train_tokenized,
    eval_dataset=eval_tokenized,
    data_collator=data_collator
)

print("Starting training...")
print("Progress can be monitored with: tensorboard --logdir ./model/runs")

trainer.train()

print("Training completed!")
print("Saving final model...")
trainer.save_model("./model/final")
print("Model saved to ./model/final")
