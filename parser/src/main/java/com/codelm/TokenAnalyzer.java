package com.codelm;

import com.github.javaparser.JavaToken;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.TokenRange;
import com.github.javaparser.ast.CompilationUnit;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;

public class TokenAnalyzer {
    public static class TokenStats {
        public long totalTokens;
        public Map<String, Integer> tokenTypeCounts;
        public long totalLiteralChars;
        public int maxLiteralChars;
        public int stringLiteralCount;
        public List<String> literalValues;
        public long totalIdentifierChars;
        public int maxIdentifierChars;
        public List<String> identifierValues;
        public long totalBytes;
        public long totalLiteralBytes;
        public long totalIdentifierBytes;
    }

    public static TokenStats analyze(String javaCode) throws IOException {
        CompilationUnit cu = StaticJavaParser.parse(javaCode);
        Optional<TokenRange> tokenRange = cu.getTokenRange();
        if (tokenRange.isEmpty()) {
            System.out.println("No tokens found");
            return null;
        }

        TokenStats stats = new TokenStats();

        stats.totalTokens = 0;
        stats.tokenTypeCounts = new HashMap<>();

        // Literal/Identifier stats
        stats.totalLiteralChars = 0;
        stats.maxLiteralChars = 0;
        stats.stringLiteralCount = 0;
        stats.totalIdentifierChars = 0;
        stats.maxIdentifierChars = 0;

        stats.totalBytes = 0;
        stats.totalLiteralBytes = 0;
        stats.totalIdentifierBytes = 0;

        stats.literalValues = new ArrayList<>();
        stats.identifierValues = new ArrayList<>();

        Optional<JavaToken> optionalToken = Optional.of(tokenRange.get().getBegin());

        while (optionalToken.isPresent()) {
            JavaToken token = optionalToken.get();
            stats.totalTokens++;
            String category = token.getCategory().toString();
            stats.tokenTypeCounts.put(category, stats.tokenTypeCounts.getOrDefault(category, 0) + 1);

            // If token is literal/identifier, get the value - length of the string and increment the token count by the length
            // Add a counter for #bytes for literals and identifiers
            String text = token.getText();
            int charLength = text.length();
            int byteLength = text.getBytes(StandardCharsets.UTF_8).length;
            stats.totalBytes += byteLength;

            if (category.equals("LITERAL") && token.getKind() == JavaToken.Kind.STRING_LITERAL.getKind()) {
                stats.stringLiteralCount++;
                stats.literalValues.add(text);
                stats.totalLiteralChars += charLength;
                stats.totalLiteralBytes += byteLength;
                if (charLength > stats.maxLiteralChars) {
                    stats.maxLiteralChars = charLength;
                }
            } else if (category.equals("IDENTIFIER")) {
                stats.identifierValues.add(text);
                stats.totalIdentifierChars += charLength;
                stats.totalIdentifierBytes += byteLength;
                if (charLength > stats.maxIdentifierChars) {
                    stats.maxIdentifierChars = charLength;
                }
            }

            optionalToken = token.getNextToken();
        }

        return stats;
    }

    public static void main(String[] args) {
        try {
            //String javaCode = "public class Example { int x = 10; }";
            String path = "src/main/java/com/codelm/taxi/CleanTaxiRestService.java";
            String javaCode = Files.readString(Paths.get(path));
            TokenStats stats = analyze(javaCode);

            if (stats == null) {
                return;
            }

            String literalsLog = "src/main/java/com/codelm/logs/literals.log";
            Files.write(Paths.get(literalsLog), stats.literalValues, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            String identifierLog = "src/main/java/com/codelm/logs/identifiers.log";
            Files.write(Paths.get(identifierLog), stats.identifierValues, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            System.err.println("Error reading the file: " + e.getMessage());
        }
    }
}
