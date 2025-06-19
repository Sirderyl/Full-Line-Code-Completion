package com.codelm;

import com.codelm.antlr.JavaLexer;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.Vocabulary;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

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

    public static final String NEWLINE_TOKEN_KEY = "!LEX_NEWLINE";
    public static final String TRUE_TOKEN_KEY = "!LEX_TRUE";
    public static final String FALSE_TOKEN_KEY = "!LEX_FALSE";
    private short newlineId = -1;

    public ModelTokenCodec() throws IOException {
        // New line token for line preservation in binary files and store the ID
        _addTokenToMap(NEWLINE_TOKEN_KEY, "\n"); // ID 0

        this.newlineId = encodingMap.get(NEWLINE_TOKEN_KEY).id;

        // Followed with ASCII characters
        for (char c = CHAR_MIN_VALUE; c <= CHAR_MAX_VALUE; c++) {
            _addTokenToMap(asciiCharToModelTokenKey(c));
        }

        // Adding binary literal tokens so they are not added as individual characters
        _addTokenToMap(TRUE_TOKEN_KEY, "true");
        _addTokenToMap(FALSE_TOKEN_KEY, "false");

        // ANTLR lexer token types
        for (int i = 1; i <= antlrVocabulary.getMaxTokenType(); i++) {
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

        int mapSizeBeforeIdents = decodingTable.size();

        List<String> identifiers = getIdentifiers(507); // 507 as some idents are a single char - won't get added to HashMap
        for (String identifier : identifiers) {
            _addTokenToMap(identifier);
        }

        int identsAdded = decodingTable.size() - mapSizeBeforeIdents;

        System.out.println("Initialized ModelTokenCodec. Vocabulary size: " + decodingTable.size());
        System.out.println("Number of identifiers added: " + identsAdded);
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

    public String asciiCharToModelTokenKey(char c) {
        // For BPE, we want to represent all characters that can appear in identifiers
        if (c >= CHAR_MIN_VALUE && c <= CHAR_MAX_VALUE) {
            return Character.toString(c);
        }

        // replace things outside the subset of ascii with a safe alternative value.
        return Character.toString(CHAR_SUBSTITUTE);
    }

    public List<String> getIdentifiers(int count) throws IOException {
        String identifiersFilePath = "C:\\Users\\spide\\Desktop\\Repos\\Full-Line-Code-Completion\\data\\analysis_output\\identifiers_weighted_count.txt";

        FileReader fr = new FileReader(identifiersFilePath);
        BufferedReader br = new BufferedReader(fr);

        List<String> identifiers = new ArrayList<>();
        String line;
        String identifier;
        for (int i = 0; i < count; i++) {
            line = br.readLine();
            identifier = line.split(":")[0];
            identifiers.add(identifier);
        }

        return identifiers;
    }

    public ByteBuffer encode(List<Token> antlrTokens) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(antlrTokens.size() * 100);
        for (Token token : antlrTokens) {
            int tokenType = token.getType();

            // Skip EOF, comments, and whitespace
            // Omitting whitespace to save space and allow BPE merges across tokens, we rely on a formatter later
            if (tokenType == JavaLexer.EOF) continue;
            if (tokenType == JavaLexer.COMMENT || tokenType == JavaLexer.LINE_COMMENT) continue;

            // Newline tokens are part of WS, preserve those and skip the rest
            if (tokenType == JavaLexer.WS) {
                String wsText = token.getText();
                for (char c : wsText.toCharArray()) {
                    if (c == '\n') {
                        if (this.newlineId != -1) {
                            byteBuffer.putShort(this.newlineId);
                        } else {
                            System.err.println("Newline token not initialized, cannot encode");
                        }
                    }
                }
                continue;
            }

            String tokenKey = lexerTokenTypeToModelTokenKey(tokenType);
            ModelToken modelToken = encodingMap.get(tokenKey);

            if (modelToken == null) {
                System.err.println("No ModelToken found for lexer key: " + tokenKey +
                        " (ANTLR type: " + tokenType + ", text: '" + token.getText() + "'). Skipping.");
                continue;
            }

            if (tokenType == JavaLexer.BOOL_LITERAL) {
                if (token.getText().equals("true")) {
                    modelToken = encodingMap.get(TRUE_TOKEN_KEY);
                } else if (token.getText().equals("false")) {
                    modelToken = encodingMap.get(FALSE_TOKEN_KEY);
                }
                if (modelToken != null) {
                    byteBuffer.putShort(modelToken.id);
                } else {
                    System.err.println("No ModelToken for bool literal.");
                }
                continue;
            }

            if (tokenType == JavaLexer.IDENTIFIER) {
                ModelToken identToken = encodingMap.get(token.getText());
                if (identToken != null) {
                    // Just put the found identifier ID from the map
                    byteBuffer.putShort(identToken.id);
                } else {
                    // If not found, put !LEX_IDENTIFIER first and then all the characters of the ident
                    byteBuffer.putShort(modelToken.id);
                    for (char c : token.getText().toCharArray()) {
                        tokenKey = asciiCharToModelTokenKey(c);
                        modelToken = encodingMap.get(tokenKey);
                        if (modelToken != null) {
                            byteBuffer.putShort(modelToken.id);
                        } else {
                            System.err.println("No ModelToken for identifier char: '" + c +
                                    "' (key: " + tokenKey + "). Skipping char.");
                            // Add an <UNK_CHAR> token ID?
                        }
                    }
                }
                continue;
            }

            byteBuffer.putShort(modelToken.id);
        }
        return byteBuffer;
    }

    // This decode method will be used in a Python wrapper after BPE decoding gives back sequence of IDs
    public String decode(ByteBuffer byteBuffer) {
        StringBuilder sb = new StringBuilder();
        short identifierId = encodingMap.get(lexerTokenTypeToModelTokenKey(JavaLexer.IDENTIFIER)).id;
        short currentNewlineId = this.newlineId;

        // Literal IDs
        short decimalLiteralId = encodingMap.get(lexerTokenTypeToModelTokenKey(JavaLexer.DECIMAL_LITERAL)).id;
        short hexLiteralId = encodingMap.get(lexerTokenTypeToModelTokenKey(JavaLexer.HEX_LITERAL)).id;
        short octLiteralId = encodingMap.get(lexerTokenTypeToModelTokenKey(JavaLexer.OCT_LITERAL)).id;
        short binaryLiteralId = encodingMap.get(lexerTokenTypeToModelTokenKey(JavaLexer.BINARY_LITERAL)).id;
        short floatLiteralId = encodingMap.get(lexerTokenTypeToModelTokenKey(JavaLexer.FLOAT_LITERAL)).id;
        short hexFloatLiteralId = encodingMap.get(lexerTokenTypeToModelTokenKey(JavaLexer.HEX_FLOAT_LITERAL)).id;
        short charLiteralId = encodingMap.get(lexerTokenTypeToModelTokenKey(JavaLexer.CHAR_LITERAL)).id;
        short stringLiteralId = encodingMap.get(lexerTokenTypeToModelTokenKey(JavaLexer.STRING_LITERAL)).id;
        short textBlockId = encodingMap.get(lexerTokenTypeToModelTokenKey(JavaLexer.TEXT_BLOCK)).id;
        // ************

        boolean inIdentifier = false;

        while (byteBuffer.hasRemaining()) {
            short id = byteBuffer.getShort();

            // 1. Defensive check for ID bounds
            if (id < 0 || id >= decodingTable.size()) {
                System.err.println("Encountered out of bounds ID during decode: " + id);
                sb.append("<?>"); // Placeholder for unknown ID
                inIdentifier = false;
                continue;
            }

            // 2. Check for newline ID
            if (currentNewlineId != -1 && id == currentNewlineId) {
                sb.append('\n');
                inIdentifier = false;
                continue;
            }

            // 3. Skip identifier marker token
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

            // 4. If we were in an identifier and this token is a single-char ASCII
            // treat it as part of that identifier (no space).
            if (inIdentifier && token.key.length() == 1) {
                sb.append(val);
                continue;
            }

            // 5. Otherwise, if we were in an identifier that just ended
            if (inIdentifier) {
                sb.append(" "); // Space after identifier
                inIdentifier = false;
            }

            // 6. Handle literals
            if (id == decimalLiteralId || id == hexLiteralId || id == octLiteralId || id == binaryLiteralId ||
                    id == floatLiteralId || id == hexFloatLiteralId) {
                sb.append("0 ");
                continue;
            }
            if (id == charLiteralId) {
                sb.append("'x' ");
                continue;
            }
            if (id == stringLiteralId || id == textBlockId) {
                sb.append("\"\" ");
                continue;
            }

            // 7. Complete the token and add space
            sb.append(val).append(" ");
        }

        return sb.toString().trim();
    }

    public String decodeToIds(ByteBuffer byteBuffer) {
        StringBuilder sb = new StringBuilder();
        while (byteBuffer.hasRemaining()) {
            short id = byteBuffer.getShort();
            if (this.newlineId != -1 && id == newlineId) {
                sb.append(id).append("\n");
            } else {
                sb.append(id).append(" ");
            }
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
        /*
        String input = "public static void foo(String bar) {}";
        String input2 = "public String input = \"test string123\";";
        String input3 = "public Integer i = new Integer(35);";
        String input4 = "SharedPreferences";
         */

        String javaInput;

        String dirPath = "C:\\Users\\spide\\Desktop\\Repos\\Full-Line-Code-Completion\\data\\clean_extract\\with_idents_50\\";
        String corpusInputPath = dirPath + "corpus2.txt";
        String corpusBinaryOutputPath = dirPath + "corpus2_encoded.dat";
        String idCorpusDecodedOutputPath = dirPath + "corpus2_ids.txt";
        String corpusDecodedInputPath = dirPath + "corpus2_decoded.txt";

        File inputFile = new java.io.File(corpusInputPath);

        if (inputFile.isFile() && inputFile.canRead()) {
            javaInput = new String(Files.readAllBytes(Paths.get(corpusInputPath)));
        } else {
            System.err.println("Can't read corpus file: " + corpusInputPath);
            return;
        }

        ModelTokenCodec codec = new ModelTokenCodec();
        List<Token> lexerTokens = lex(javaInput);
        ByteBuffer byteSequence = codec.encode(lexerTokens);
        System.out.println("Finished encoding. ByteBuffer position: " + byteSequence.position() + ", limit: " +
                byteSequence.limit() + ", capacity: " + byteSequence.capacity());

        byteSequence.flip();

        try (FileOutputStream fos = new FileOutputStream(corpusBinaryOutputPath)) {
            byte[] bytesToWrite = new byte[byteSequence.remaining()];
            byteSequence.get(bytesToWrite);
            fos.write(bytesToWrite);
        }

        byte[] binaryCorpusBytes;
        try {
            binaryCorpusBytes = Files.readAllBytes(Paths.get(corpusBinaryOutputPath));
        } catch (IOException e) {
            System.err.println("Can't read corpus file: " + corpusBinaryOutputPath);
            throw new RuntimeException(e);
        }

        ByteBuffer bufferToDecode = ByteBuffer.wrap(binaryCorpusBytes);
        String idCorpus = codec.decodeToIds(bufferToDecode);

        try {
            Files.writeString(Paths.get(idCorpusDecodedOutputPath), idCorpus);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        /*
        ModelTokenCodec codec = new ModelTokenCodec();
        List<Token> input2Tokens = lex(input4);
        ByteBuffer input2Buffer = codec.encode(input2Tokens);
        input2Buffer.flip();
        String input2Ids = codec.decodeToIds(input2Buffer);

         */

        /*
        String mapVocabPath = dirPath + "mapVocabWithIdents.txt";
        int i = 0;
        StringBuilder sb = new StringBuilder();
        for (String key : codec.encodingMap.keySet()) {
            ModelToken token = codec.encodingMap.get(key);
            sb.append(i).append(" ").append(key).append("\n");
            i++;
        }
        Files.writeString(Paths.get(mapVocabPath), sb.toString());

         */

        bufferToDecode.rewind();

        String reconstructedJava = codec.decode(bufferToDecode);

        Parser parser = new Parser();
        reconstructedJava = parser.formatJavaCode(reconstructedJava);

        try {
            Files.writeString(Paths.get(corpusDecodedInputPath), reconstructedJava);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        /*
        System.out.println(input4);
        System.out.println(input2Ids);
        //System.out.println(decoded);

         */
    }
}
