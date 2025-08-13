package com.codelm;

import com.codelm.antlr.JavaLexer;
import org.antlr.v4.runtime.*;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

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
    private final Map<String, ModelToken> encodingMap = new LinkedHashMap<>();
    private final Vocabulary antlrVocabulary = JavaLexer.VOCABULARY; // Cache for efficiency

    public static final char CHAR_MIN_VALUE = 32;
    public static final char CHAR_MAX_VALUE = 126;
    public static final char CHAR_SUBSTITUTE = '_';
    public static final int PUA_START = 0xE000;

    public static final String NEWLINE_TOKEN_KEY = "!LEX_NEWLINE";
    public static final String TRUE_TOKEN_KEY = "!LEX_TRUE";
    public static final String FALSE_TOKEN_KEY = "!LEX_FALSE";
    private short newlineId = -1;

    public ModelTokenCodec(int identifiersToAdd) throws IOException {
        // New line token for line preservation in binary files and store the ID
        _addTokenToMap(NEWLINE_TOKEN_KEY, "<EOL>"); // ID 0

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

        if (identifiersToAdd > 0) {
            List<String> identifiers = getIdentifiers(identifiersToAdd);
            for (String identifier : identifiers) {
                _addTokenToMap(identifier);
            }
        }

        int identsAdded = decodingTable.size() - mapSizeBeforeIdents;

        //System.out.println("Initialized ModelTokenCodec. Vocabulary size: " + decodingTable.size());
        //System.out.println("Number of identifiers added: " + identsAdded);
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
        String identifiersFilePath = "C:\\Users\\spide\\Desktop\\Repos\\Full-Line-Code-Completion\\custom_bpe_0\\assets\\missing_idents.txt";

        FileReader fr = new FileReader(identifiersFilePath);
        BufferedReader br = new BufferedReader(fr);

        List<String> identifiers = new ArrayList<>();
        String line;
        String identifier;
        for (int i = 0; i < count; i++) {
            line = br.readLine();
            //identifier = line.split(":")[0];
            identifiers.add(line);
        }

        return identifiers;
    }

    /**
     * Encodes the string content of a Java file directly into a string of Unicode PUA characters.
     * This method works entirely in-memory to maximize performance.
     *
     * @param javaContent The string content of the Java file.
     * @return A string where each character represents a token ID, mapped to the PUA.
     * @throws IOException If the lexer encounters an issue.
     */
    public String encodeFileToUnicodeString(String javaContent) throws IOException {
        JavaLexer lexer = new JavaLexer(CharStreams.fromString(javaContent));
        StringBuilder unicodeBuilder = new StringBuilder();

        for (Token token : lexer.getAllTokens()) {
            if (token.getType() == Token.EOF) {
                break;
            }

            int tokenType = token.getType();
            String tokenText = token.getText();

            if (tokenType == JavaLexer.COMMENT || tokenType == JavaLexer.LINE_COMMENT) continue;

            if (tokenType == JavaLexer.WS) {
                for (char c : tokenText.toCharArray()) {
                    if (c == '\n') {
                        appendIdAsPua(unicodeBuilder, this.newlineId);
                        unicodeBuilder.append('\n'); // Preserve line breaks in output
                    }
                }
                continue;
            }

            String tokenKey = lexerTokenTypeToModelTokenKey(tokenType);
            ModelToken modelToken = encodingMap.get(tokenKey);

            if (modelToken == null) continue;

            if (tokenType == JavaLexer.BOOL_LITERAL) {
                ModelToken boolToken = encodingMap.get(tokenText.equals("true") ? TRUE_TOKEN_KEY : FALSE_TOKEN_KEY);
                if (boolToken != null) {
                    appendIdAsPua(unicodeBuilder, boolToken.id);
                }
                continue;
            }

            if (tokenType == JavaLexer.IDENTIFIER) {
                ModelToken identToken = encodingMap.get(tokenText);
                if (identToken != null) {
                    appendIdAsPua(unicodeBuilder, identToken.id);
                } else {
                    appendIdAsPua(unicodeBuilder, modelToken.id); // IDENTIFIER token
                    for (char c : tokenText.toCharArray()) {
                        ModelToken charModelToken = encodingMap.get(asciiCharToModelTokenKey(c));
                        if (charModelToken != null) {
                            appendIdAsPua(unicodeBuilder, charModelToken.id);
                        }
                    }
                }
                continue;
            }

            appendIdAsPua(unicodeBuilder, modelToken.id);
        }
        return unicodeBuilder.toString();
    }

    /**
     * Decodes a string of Unicode PUA characters back into Java code.
     * This is the reverse operation of {@link #encodeFileToUnicodeString(String)}.
     *
     * @param unicodeString The PUA-encoded string.
     * @return The reconstructed Java code as a string.
     */
    public String decodeUnicodeStringToJava(String unicodeString) {
        StringBuilder sb = new StringBuilder();
        // Cache token IDs for efficiency inside the loop.
        short identifierId = encodingMap.get(lexerTokenTypeToModelTokenKey(JavaLexer.IDENTIFIER)).id;

        // Literal IDs for placeholder substitution
        short decimalLiteralId = encodingMap.get(lexerTokenTypeToModelTokenKey(JavaLexer.DECIMAL_LITERAL)).id;
        short hexLiteralId = encodingMap.get(lexerTokenTypeToModelTokenKey(JavaLexer.HEX_LITERAL)).id;
        short octLiteralId = encodingMap.get(lexerTokenTypeToModelTokenKey(JavaLexer.OCT_LITERAL)).id;
        short binaryLiteralId = encodingMap.get(lexerTokenTypeToModelTokenKey(JavaLexer.BINARY_LITERAL)).id;
        short floatLiteralId = encodingMap.get(lexerTokenTypeToModelTokenKey(JavaLexer.FLOAT_LITERAL)).id;
        short hexFloatLiteralId = encodingMap.get(lexerTokenTypeToModelTokenKey(JavaLexer.HEX_FLOAT_LITERAL)).id;
        short charLiteralId = encodingMap.get(lexerTokenTypeToModelTokenKey(JavaLexer.CHAR_LITERAL)).id;
        short stringLiteralId = encodingMap.get(lexerTokenTypeToModelTokenKey(JavaLexer.STRING_LITERAL)).id;
        short textBlockId = encodingMap.get(lexerTokenTypeToModelTokenKey(JavaLexer.TEXT_BLOCK)).id;

        boolean inIdentifier = false;

        for (char puaChar : unicodeString.toCharArray()) {
            // The encoder adds a literal '\n' after the PUA newline character for readability.
            // We can skip the literal '\n' and just handle the PUA character that represents a newline.
            if (puaChar == '\n') {
                continue;
            }

            // Convert the PUA character back to a token ID.
            short id = (short) (puaChar - PUA_START);
            //System.out.print(id + " ");

            // Defensive check for out-of-bounds IDs.
            if (id < 0 || id >= decodingTable.size()) {
                sb.append("<?>"); // Placeholder for an unknown or invalid token
                //System.err.println("Invalid ID: " + id + " Decoding table size: " + decodingTable.size() + " PUA Char: " + puaChar);
                inIdentifier = false;
                continue;
            }

            // Handle newline tokens.
            if (this.newlineId != -1 && id == this.newlineId) {
                sb.append('\n');
                inIdentifier = false;
                continue;
            }

            // Handle the start of an identifier.
            if (id == identifierId) {
                if (inIdentifier) {
                    sb.append(" "); // Space between adjacent identifiers
                }
                inIdentifier = true;
                continue;
            }

            ModelToken token = decodingTable.get(id);
            String value = token.value;

            // If we are currently building an identifier and the token is a single character,
            // append it directly without a space.
            if (inIdentifier && token.key.length() == 1) {
                sb.append(value);
                continue;
            }

            // If we were in an identifier and the new token is not a character part of it,
            // the identifier has ended. Add a space before processing the new token.
            if (inIdentifier) {
                sb.append(" ");
                inIdentifier = false;
            }

            // Handle placeholder replacements for literals.
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

            // Append the token's value followed by a space.
            sb.append(value).append(" ");
        }

        return sb.toString().trim();
    }

    /**
     * Helper to convert an ID to a PUA character and append it to a StringBuilder.
     */
    private void appendIdAsPua(StringBuilder builder, short id) {
        if (id >= 0 && (PUA_START + id) <= Character.MAX_VALUE) {
            builder.append((char) (PUA_START + id));
        }
    }

    public String decodeIds(String idString) {
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

        String[] idTokens = idString.trim().split("[ \\n]+");

        for (String idToken : idTokens) {
            if (idToken.isEmpty()) {
                continue;
            }

            short id;
            try {
                id = Short.parseShort(idToken);
            } catch (NumberFormatException e) {
                System.err.println("Invalid ID format during decode: " + idToken);
                sb.append("<?>"); // Placeholder for invalid ID
                inIdentifier = false;
                continue;
            }

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

    public void writeVocabulary(String outputPath) throws IOException {
        StringBuilder sb = new StringBuilder();
        // Iterate over the decodingTable which is already ordered by ID
        for (ModelToken token : decodingTable) {
            sb.append(token.id)
                    .append(" ")
                    .append(token.key.replace("\n", "\\n")) // Sanitize newlines in key
                    .append(" ")
                    .append(token.value.replace("\n", "\\n")) // Sanitize newlines in value
                    .append("\n");
        }
        Files.writeString(Paths.get(outputPath), sb.toString(), StandardCharsets.UTF_8);
    }

    private static void printProgressBar(int processedCount, int total, long startTime) {
        int percent = (int) (((double) processedCount / total) * 100);
        StringBuilder bar = new StringBuilder("[");
        int progress = (int) (((double) processedCount / total) * 50); // 50 chars bar
        for (int i = 0; i < 50; i++) {
            bar.append(i < progress ? "=" : (i == progress ? ">" : " "));
        }
        bar.append("] ")
                .append(percent)
                .append("% (")
                .append(processedCount)
                .append("/")
                .append(total)
                .append(")");

        // --- ETA Calculation ---
        String etaStr = "ETA: Calculating...";
        if (processedCount > 0) {
            long elapsedTimeMs = System.currentTimeMillis() - startTime;
            double timePerFileMs = (double) elapsedTimeMs / processedCount;
            long remainingTimeMs = (long) (timePerFileMs * (total - processedCount));
            etaStr = "ETA: " + formatDuration(remainingTimeMs);
        }
        bar.append(" ").append(etaStr);

        System.out.print("\r" + bar.toString());
    }

    private static String formatDuration(long millis) {
        if (millis < 0) {
            return "N/A";
        }
        long hours = java.util.concurrent.TimeUnit.MILLISECONDS.toHours(millis);
        long minutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        long seconds = java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(millis) % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    /**
     * Processes dataset in zip files in parallel, encodes using encodeFileToUnicodeString and outputs to subfolders.
     *
     * @param inputDirectoryPath
     * @param outputDirectoryPath
     * @throws IOException
     */
    private static void encodeZip(String inputDirectoryPath, String outputDirectoryPath) throws IOException {
        ModelTokenCodec codec = new ModelTokenCodec(0);

        System.out.println("Scanning for Java files in: " + inputDirectoryPath);

        List<Path> zipFiles;
        try (Stream<Path> paths = Files.list(Paths.get(inputDirectoryPath))) {
            zipFiles = paths
                    .filter(path -> path.toString().toLowerCase().endsWith(".zip"))
                    .filter(Files::isRegularFile)
                    .collect(Collectors.toList());
        }

        if (zipFiles.isEmpty()) {
            System.out.println("No ZIP files found.");
            return;
        }

        // First, count all the java files in all zips for an accurate progress bar.
        System.out.println("Calculating total number of Java files...");
        long totalJavaFiles = zipFiles.parallelStream()
                .mapToLong(zipFilePath -> {
                    try (ZipFile zipFile = new ZipFile(zipFilePath.toFile())) {
                        return zipFile.stream()
                                .filter(entry -> !entry.isDirectory() && entry.getName().endsWith(".java"))
                                .count();
                    } catch (IOException e) {
                        System.err.println("\nWarning: Could not read zip file for counting: " + zipFilePath + ". Skipping.");
                        return 0;
                    }
                })
                .sum();

        if (totalJavaFiles == 0) {
            System.out.println("No Java files found within the provided ZIP archives.");
            return;
        }

        System.out.println(totalJavaFiles + " Java files found in " + zipFiles.size() + " ZIPs. Starting parallel processing...");

        AtomicInteger processedCount = new AtomicInteger(0);
        long startTime = System.currentTimeMillis();
        final int totalFilesForBar = (int) totalJavaFiles; // For use in lambda
        printProgressBar(0, totalFilesForBar, startTime);

        // Process zip files in parallel.
        zipFiles.parallelStream().forEach(zipFilePath -> {
            String zipFileName = zipFilePath.getFileName().toString();
            String zipNameWithoutExt = zipFileName.substring(0, zipFileName.lastIndexOf('.'));
            Path zipOutputBaseDir = Paths.get(outputDirectoryPath, zipNameWithoutExt);

            try (ZipFile zipFile = new ZipFile(zipFilePath.toFile())) {
                zipFile.stream()
                        .parallel() // Process entries within a single zip in parallel
                        .filter(zipEntry -> !zipEntry.isDirectory() && zipEntry.getName().endsWith(".java"))
                        .forEach(javaEntry -> {
                            try {
                                // Determine output path, preserving the zip's internal directory structure within the new subfolder.
                                Path relativePath = Paths.get(javaEntry.getName());
                                Path outputSubDirPath = zipOutputBaseDir;
                                if (relativePath.getParent() != null) {
                                    outputSubDirPath = Paths.get(zipOutputBaseDir.toString(), relativePath.getParent().toString());
                                }
                                Files.createDirectories(outputSubDirPath);

                                String fileNameWithoutExt = relativePath.getFileName().toString().replace(".java", "");
                                String unicodeOutputPath = Paths.get(outputSubDirPath.toString(), fileNameWithoutExt + "_unicode.txt").toString();

                                // Read file content from the zip entry.
                                String content;
                                try (InputStream is = zipFile.getInputStream(javaEntry);
                                     BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                                    content = reader.lines()
                                            .map(String::strip)
                                            .filter(line -> !line.isEmpty())
                                            .collect(Collectors.joining("\n"));
                                }

                                // Process and write the file if it's not empty.
                                if (!content.isEmpty()) {
                                    String finalContent = content + "\n";
                                    String unicodeString = codec.encodeFileToUnicodeString(finalContent);
                                    Files.writeString(Paths.get(unicodeOutputPath), unicodeString, StandardCharsets.UTF_8);
                                }
                            } catch (IOException e) {
                                System.err.println("\nFailed to process entry " + javaEntry.getName() + " in zip " + zipFilePath + ": " + e.getMessage());
                            } finally {
                                // Increment counter and update progress bar for each file.
                                int count = processedCount.incrementAndGet();
                                printProgressBar(count, totalFilesForBar, startTime);
                            }
                        });
            } catch (IOException e) {
                System.err.println("\nFailed to open or read zip file " + zipFilePath + ": " + e.getMessage());
            }
        });

        System.out.println("\n------------------------------------");
        System.out.println("All files processed successfully.");

        // Write the vocabulary mapping file to the root of the output directory.
        String mapVocabPath = Paths.get(outputDirectoryPath, "mapVocab.txt").toString();
        System.out.println("Writing vocabulary file to: " + mapVocabPath);
        codec.writeVocabulary(mapVocabPath);
        System.out.println("------------------------------------");
    }

    public static void main(String[] args) throws IOException {
        /*
        Compile to jar with (replace directory with your copy of ANTLR):
        javac -cp ".\antlr-4.13.2-complete.jar" target/generated-sources/antlr4/com/codelm/antlr/JavaLexer.java src/main/java/com/codelm/ModelTokenCodec.java -d out
        jar cfe ModelTokenCodec.jar com.codelm.ModelTokenCodec -C out .
         */

        // If no arguments are provided, print usage guide and exit.
        if (args.length < 1) {
            System.err.println("Usage: java -jar ModelTokenCodec.jar <mode> [options...]");
            System.err.println("Modes:");
            System.err.println("  encode                  - Reads Java code from stdin and prints encoded Unicode to stdout.");
            System.err.println("  decode                  - Reads encoded Unicode from stdin and prints decoded Java to stdout.");
            System.err.println("  batchEncode             - Encodes all .zip files in inputDirectoryPath and saves to outputDirectoryPath.");
            System.err.println("  testDecode              - Runs a built-in decoding test with a sample Unicode string.");
            System.exit(1);
        }

        String mode = args[0];
        ModelTokenCodec codec = new ModelTokenCodec(0);

        switch (mode) {
            case "encode":
                try (Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8)) {
                    String inputContent = scanner.useDelimiter("\\A").next();
                    System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
                    String encoded = codec.encodeFileToUnicodeString(inputContent);
                    System.out.println(encoded);
                }
                break;

            case "decode":
                try (Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8)) {
                    String inputContent = scanner.useDelimiter("\\A").next();
                    System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
                    String decoded = codec.decodeUnicodeStringToJava(inputContent);
                    System.out.println(decoded);
                }
                break;

            case "batchEncode":
                if (args.length != 1) {
                    System.err.println("Error: Incorrect arguments for batchEncode mode.");
                    System.err.println("Usage: java -jar ModelTokenCodec.jar batchEncode");
                    System.exit(1);
                }
                String inputDirectoryPath = "D:\\dataset\\javazip";
                String outputDirectoryPath = "C:\\Users\\spide\\Desktop\\Repos\\Full-Line-Code-Completion\\data\\processed_dataset";
                System.out.println("Starting batch encoding...");
                System.out.println("Input Directory: " + inputDirectoryPath);
                System.out.println("Output Directory: " + outputDirectoryPath);
                encodeZip(inputDirectoryPath, outputDirectoryPath);

                String mapVocabPath = outputDirectoryPath + "mapVocab.txt";
                int i = 0;
                StringBuilder sb = new StringBuilder();
                for (String key : codec.encodingMap.keySet()) {
                    ModelToken token = codec.encodingMap.get(key);
                    sb.append(i).append(" ").append(key).append(" ").append(token.value).append("\n");
                    i++;
                }
                Files.writeString(Paths.get(mapVocabPath), sb.toString());

                break;

            case "testDecode":
                System.out.println("Running built-in decode test...");
                String inputContent = "\uE06A\uE0E1\uE034\uE050\uE04D\uE056\uE055\uE04A\uE050\uE04F\uE0B1\uE000";
                String decoded = codec.decodeUnicodeStringToJava(inputContent);
                System.out.println("--- Decoded Output ---");
                System.out.println(decoded);
                System.out.println("----------------------");
                break;

            default:
                System.err.println("Error: Invalid mode '" + mode + "'.");
                System.err.println("Run without arguments to see the list of available modes.");
                System.exit(1);
                break;
        }
    }
}
