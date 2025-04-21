package uk.ac.newcastle.enterprisemiddleware;

import com.github.javaparser.JavaToken;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.TokenRange;
import com.github.javaparser.ast.CompilationUnit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class TokenAnalyzer {
    public static void analyze(String javaCode) {
        CompilationUnit cu = StaticJavaParser.parse(javaCode);
        Optional<TokenRange> tokenRange = cu.getTokenRange();
        if (!tokenRange.isPresent()) {
            System.out.println("No tokens found");
            return;
        }

        Map<String, Integer> tokenTypeCounts = new HashMap<>();
        // Storing specific tokens per category
        Map<String, List<String>> tokenTypes = new HashMap<>();
        int totalTokens = 0;
        Optional<JavaToken> optionalToken = Optional.of(tokenRange.get().getBegin());

        while (optionalToken.isPresent()) {
            JavaToken token = optionalToken.get();
            totalTokens++;
            // If token is literal/identifier, get the value - length of the string and increment the token count by the length
            // Add a counter for #bytes for literals and identifiers
            String category = token.getCategory().toString();
            tokenTypeCounts.put(category, tokenTypeCounts.getOrDefault(category, 0) + 1);
            tokenTypes.computeIfAbsent(category, k -> new ArrayList<>()).add(token.getText());
            optionalToken = token.getNextToken();
        }

        System.out.println("Total tokens: " + totalTokens);
        System.out.println("Token type breakdown:");
        for (Map.Entry<String, Integer> entry : tokenTypeCounts.entrySet()) {
            String category = entry.getKey();
            int count = entry.getValue();
            List<String> tokens = tokenTypes.get(category);
            System.out.println(entry.getKey() + ": " + entry.getValue());
            //System.out.println(category + ": " + count + " (Tokens: " + tokens + ")");
        }
    }

    public static void main(String[] args) {
        try {
            //String javaCode = "public class Example { int x = 10; }";
            String path = "src/main/java/uk/ac/newcastle/enterprisemiddleware/taxi/CleanTaxiRestService.java";
            String javaCode = Files.readString(Paths.get(path));
            analyze(javaCode);
        } catch (IOException e) {
            System.err.println("Error reading the file: " + e.getMessage());
        }
    }
}
