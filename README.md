# Full-Line-Code-Completion

Install dependencies:

```bash
pip install -r requirements.txt
```

Pull model files:
```bash
git lfs pull
```

## To run the model in inference mode

```bash
python model_inference.py
```

You can change the prompt given to the model in the `prompt` variable.

## To run the whole training pipeline

1. Download and extract the dataset (will probably need Hugging Face login):
    ```bash
    python dataset_extract.py
    ```

2. Run the data preprocessing pipeline to clean the dataset and get it ready for tokenization with the `ParsingText.java` file

3. Pre-tokenize the dataset and convert it to Unicode characters. Run the `ModelTokenCodec.java` file either inside an IDE with the `batchEncode` argument or by using the below command:

    Windows:
    ```bash
    java -cp ".\ModelTokenCodec.jar;.\antlr-4.13.2-complete.jar" com.codelm.ModelTokenCodec batchEncode
    ```

    Linux / macOS:
    ```bash
    java -cp "./ModelTokenCodec.jar:./antlr-4.13.2-complete.jar" com.codelm.ModelTokenCodec batchEncode
    ```

4. We use 5% of the total dataset for tokenizer training. The HF tokenizers library expects a single file to be passed in the API. Join 5% of the dataset into a single file with:
    ```bash
    python concat_corpus_multithread.py
    ```

5. Train the BPE tokenizer with:
    ```bash
    python train_bpe.py
    ```

6. Finally, train the language model with:
    ```bash
    python train.py
    ```