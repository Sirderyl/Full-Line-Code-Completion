package com.codelm;

import com.codelm.antlr.JavaLexer;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.Vocabulary;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModelTokenCodec {

    public static class ModelToken {
        public final short id;
        public final String key; // Key used in encodingMap ("!LEX_PUBLIC" etc.)
        public final String value; // Value for reconstruction ("public" ...)

        public ModelToken(short id, String key, String value) {
            this.id = id;
            this.key = key;
            this.value = value;
        }

        public ModelToken(short id, String key) {
            this(id, key, key);
        }
    }

    private final List<ModelToken> decodingTable = new ArrayList<>();
    private final Map<String, ModelToken> encodingMap = new HashMap<>();
    private final Vocabulary antlrVocabulary = JavaLexer.VOCABULARY; // Cache for efficiency

    public static final char CHAR_MIN_VALUE = 32;
    public static final char CHAR_MAX_VALUE = 126;
    public static final char CHAR_SUBSTITUTE = '_';


    public ModelTokenCodec() {
        // Begin with special tokens, used for <PAD>, <UNK>, <BOS>, <EOS>
        _addTokenToMap("example_special_token", "example_special_token_value"); // ID 0

        // Followed with ASCII characters
        for (char c = CHAR_MIN_VALUE; c <= CHAR_MAX_VALUE; c++) {
            _addTokenToMap(asciiCharToModelTokenKey(c));
        }

        /**
         * Add control characters? e.g. \t, \n as !LEX_TAB, !LEX_NEWLINE...
         */

        // ANTLR lexer token types
        for (int i = 0; i <= antlrVocabulary.getMaxTokenType(); i++) {
            // For a consistent key, symbolic name is best
            String symbolicName = antlrVocabulary.getSymbolicName(i);
            if (symbolicName != null) {
                String literalName = antlrVocabulary.getLiteralName(i);
                String valueForReconstruction;
                if (literalName != null) {
                    valueForReconstruction = literalName.replace("'", ""); // Remove quotes like 'public' -> public
                } else {
                    valueForReconstruction = symbolicName;
                }
                _addTokenToMap(lexerTokenTypeToModelTokenKey(i), valueForReconstruction);
            } else {
                System.err.println("Null symbolic name!");
            }
        }
        System.out.println("Initialized ModelTokenCodec. Vocabulary size: " + decodingTable.size());
    }

    private void _addTokenToMap(String key, String value) {
        if (!encodingMap.containsKey(key)) {
            short id = (short) decodingTable.size();
            ModelToken modelToken = new ModelToken(id, key, value);
            decodingTable.add(modelToken);
            encodingMap.put(key, modelToken);
        }
    }

    private void _addTokenToMap(String key) {
        _addTokenToMap(key, key);
    }

    public String lexerTokenTypeToModelTokenKey(int type) {
        String symbolicName = antlrVocabulary.getSymbolicName(type);
        if (symbolicName == null) {
            return "!LEX_UNKNOWN_" + type;
        }
        return "!LEX_" + symbolicName;
    }

    public static String asciiCharToModelTokenKey(char c) {
        // For BPE, we want to represent all characters that can appear in identifiers
        if (c >= CHAR_MIN_VALUE && c <= CHAR_MAX_VALUE) {
            return Character.toString(c);
        }

        // replace things outside the subset of ascii with a safe alternative value.
        return Character.toString(CHAR_SUBSTITUTE);
    }

    public List<Short> encode(List<Token> antlrTokens) {
        List<Short> idSequence = new ArrayList<>();
        for (Token token : antlrTokens) {
            int tokenType = token.getType();

            // Skip EOF, comments, and whitespace
            // Omitting whitespace to save space and allow BPE merges across tokens, we rely on a formatter later
            if (tokenType == JavaLexer.EOF) continue;
            if (tokenType == JavaLexer.COMMENT || tokenType == JavaLexer.LINE_COMMENT) continue;
            if (tokenType == JavaLexer.WS) continue;

            String lexerKey = lexerTokenTypeToModelTokenKey(tokenType);
            ModelToken modelToken = encodingMap.get(lexerKey);

            if (modelToken == null) {
                System.err.println("No ModelToken found for lexer key: " + lexerKey +
                        " (ANTLR type: " + tokenType + ", text: '" + token.getText() + "'). Skipping.");
                continue;
            }

            idSequence.add(modelToken.id);

            if (tokenType == JavaLexer.IDENTIFIER) {
                for (char c : token.getText().toCharArray()) {
                    String charKey = asciiCharToModelTokenKey(c);
                    ModelToken charModelToken = encodingMap.get(charKey);
                    if (charModelToken != null) {
                        idSequence.add(charModelToken.id);
                    } else {
                        System.err.println("No ModelToken for identifier char: '" + c +
                                "' (key: " + charKey + "). Skipping char.");
                        // Add an <UNK_CHAR> token ID?
                    }
                }
            }
        }
        return idSequence;
    }

    // This decode method will be used in a Python wrapper after BPE decoding gives back sequence of IDs
    public String decode(List<Short> idSequence) {
        StringBuilder sb = new StringBuilder();
        short identifierId = encodingMap.get(lexerTokenTypeToModelTokenKey(JavaLexer.IDENTIFIER)).id;

        boolean inIdentifier = false;
        for (short id : idSequence) {
            // 1. Skip identifier marker token
            if (id == identifierId) {
                // If we're already in an identifier, that means the last one just ended
                // Insert a space before starting the next one
                if (inIdentifier) {
                    sb.append(" ");
                }
                inIdentifier = true;
                continue;
            }

            ModelToken token = decodingTable.get(id);
            String val = token.value;

            // 2. If we were in an identifier and this token is a single-char ASCII
            // treat it as part of that identifier (no space).
            if (inIdentifier && token.key.length() == 1) {
                sb.append(val);
                continue;
            }

            // 3. Otherwise, if we were in an identifier that just ended
            if (inIdentifier) {
                sb.append(" "); // Space after identifier
                inIdentifier = false;
            }

            // 4. Complete the token and add space
            sb.append(val).append(" ");
        }

        return sb.toString().trim();
    }

    public static List<Token> lex(String input) {
        try {
            JavaLexer lexer = new JavaLexer(CharStreams.fromString(input));
            CommonTokenStream commonTokenStream = new CommonTokenStream(lexer);
            commonTokenStream.fill();
            return commonTokenStream.getTokens();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws IOException {
        String input = "public static void foo(String bar) {}";
        String input2 = "public String input = \"test string123\";";
        String input3 = "public Integer i = new Integer(35);";

        String javaInput;
        String dirPath = "C:\\Users\\spide\\Desktop\\Repos\\Full-Line-Code-Completion\\data\\clean_extract\\";
        String corpusInput = dirPath + "corpus2.txt";
        String corpusOutput = dirPath + "corpus2_encoded.txt";
        File inputFile = new java.io.File(corpusInput);
        if (inputFile.isFile() && inputFile.canRead()) {
            javaInput = new String(Files.readAllBytes(Paths.get(corpusInput)));
        } else {
            javaInput = null;
        }

        ModelTokenCodec codec = new ModelTokenCodec();
        List<Token> lexerTokens = lex(input);
        List<Short> idSequence = codec.encode(lexerTokens);
        for (Short id : idSequence) {
            Files.writeString(Paths.get(corpusOutput), id.toString());
        }

        String decoded = codec.decode(idSequence);

        System.out.println(input);
        System.out.println(idSequence);
        System.out.println(decoded);
    }
}
