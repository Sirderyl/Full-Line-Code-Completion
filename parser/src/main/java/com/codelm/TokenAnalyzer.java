package com.codelm;

import com.github.javaparser.JavaToken;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.TokenRange;
import com.github.javaparser.ast.CompilationUnit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;

public class TokenAnalyzer {
    public static class TokenStats {
        public int totalTokens;
        public Map<String, Integer> tokenTypeCounts;
        public int totalLiteralChars;
        public int maxLiteralChars;
        public List<String> literalValues;
        public Map<String, Integer> literalFrequencies;
        public int totalIdentifierChars;
        public int maxIdentifierChars;
        public List<String> identifierValues;
        public Map<String, Integer> identifierFrequencies;
    }

    public static TokenStats analyze(String javaCode) {
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
        stats.totalIdentifierChars = 0;
        stats.maxIdentifierChars = 0;

        stats.literalValues = new ArrayList<>();
        stats.literalFrequencies = new HashMap<>();
        stats.identifierValues = new ArrayList<>();
        stats.identifierFrequencies = new HashMap<>();

        Optional<JavaToken> optionalToken = Optional.of(tokenRange.get().getBegin());

        while (optionalToken.isPresent()) {
            JavaToken token = optionalToken.get();
            stats.totalTokens++;
            String category = token.getCategory().toString();
            stats.tokenTypeCounts.put(category, stats.tokenTypeCounts.getOrDefault(category, 0) + 1);

            // If token is literal/identifier, get the value - length of the string and increment the token count by the length
            // Add a counter for #bytes for literals and identifiers
            int length = token.getText().length();
            String text = token.getText();
            if (category.equals("LITERAL")) {
                stats.literalValues.add(text);
                stats.literalFrequencies.put(text, stats.literalFrequencies.getOrDefault(text, 0) + 1);
                stats.totalLiteralChars += length;
                if (length > stats.maxLiteralChars) {
                    stats.maxLiteralChars = length;
                }
            } else if (category.equals("IDENTIFIER")) {
                stats.identifierValues.add(text);
                stats.identifierFrequencies.put(text, stats.identifierFrequencies.getOrDefault(text, 0) + 1);
                stats.totalIdentifierChars += length;
                if (length > stats.maxIdentifierChars) {
                    stats.maxIdentifierChars = length;
                }
            }

            optionalToken = token.getNextToken();
        }

        // Calculating the average literal/identifier lengths
        double avgLiteralLength = (double) stats.totalLiteralChars / stats.tokenTypeCounts.get("LITERAL");
        double avgIdentifierLength = (double) stats.totalIdentifierChars / stats.tokenTypeCounts.get("IDENTIFIER");

        // Output results
        System.out.println("Total tokens: " + stats.totalTokens);
        System.out.println("Token type breakdown:");
        for (Map.Entry<String, Integer> entry : stats.tokenTypeCounts.entrySet()) {
            System.out.println(entry.getKey() + ": " + entry.getValue());
        }

        System.out.println("\nLiteral stats:");
        System.out.println("Total literal chars: " + stats.totalLiteralChars);
        System.out.println("Average literal chars: " + avgLiteralLength);
        System.out.println("Max literal chars: " + stats.maxLiteralChars);

        System.out.println("\nIdentifier stats:");
        System.out.println("Total identifier chars: " + stats.totalIdentifierChars);
        System.out.println("Average identifier chars: " + avgIdentifierLength);
        System.out.println("Max identifier chars: " + stats.maxIdentifierChars);

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

            System.out.println("\nTop 10 most frequent literals:");
            stats.literalFrequencies.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(10)
                    .forEach(entry -> System.out.println(entry.getKey() + ": " + entry.getValue()));

            System.out.println("\nTop 10 most frequent identifiers:");
            stats.identifierFrequencies.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(10)
                    .forEach(entry -> System.out.println(entry.getKey() + ": " + entry.getValue()));
        } catch (IOException e) {
            System.err.println("Error reading the file: " + e.getMessage());
        }
    }
}
