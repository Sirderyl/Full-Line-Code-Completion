package com.codelm;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CumulativeTokenStats {

    private long totalTokens;
    private Map<String, Integer> tokenTypeCounts;
    private long totalLiteralChars;
    private int maxLiteralChars;
    private long totalStringLiterals;
    private long totalIdentifierChars;
    private int maxIdentifierChars;
    private long totalBytes;
    private long totalLiteralBytes;
    private long totalIdentifierBytes;

    public CumulativeTokenStats() {
        this.totalTokens = 0;
        this.tokenTypeCounts = new HashMap<>();
        this.totalLiteralChars = 0;
        this.maxLiteralChars = 0;
        this.totalStringLiterals = 0;
        this.totalIdentifierChars = 0;
        this.maxIdentifierChars = 0;
        this.totalBytes = 0;
        this.totalLiteralBytes = 0;
        this.totalIdentifierBytes = 0;
    }

    public void update(TokenAnalyzer.TokenStats stats) {
        totalTokens += stats.totalTokens;

        for (Map.Entry<String, Integer> entry : stats.tokenTypeCounts.entrySet()) {
            String key = entry.getKey();
            int value = entry.getValue();
            tokenTypeCounts.put(key, tokenTypeCounts.getOrDefault(key, 0) + value);
        }

        totalLiteralChars += stats.totalLiteralChars;

        if (stats.maxLiteralChars > maxLiteralChars) {
            maxLiteralChars = stats.maxLiteralChars;
        }

        totalStringLiterals += stats.stringLiteralCount;

        totalIdentifierChars += stats.totalIdentifierChars;

        if (stats.maxIdentifierChars > maxIdentifierChars) {
            maxIdentifierChars = stats.maxIdentifierChars;
        }

        totalBytes += stats.totalBytes;
        totalLiteralBytes += stats.totalLiteralBytes;
        totalIdentifierBytes += stats.totalIdentifierBytes;
    }

    public void writeStatsToFile(String filePath) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            writer.write("Total tokens: " + totalTokens + "\n");
            writer.write("Token type breakdown:\n");
            for (Map.Entry<String, Integer> entry : tokenTypeCounts.entrySet()) {
                writer.write(entry.getKey() + ": " + entry.getValue() + "\n");
            }

            // Calculating the average literal/identifier lengths
            double avgLiteralLength = totalStringLiterals > 0 ? (double) totalLiteralChars / totalStringLiterals : 0;
            double avgIdentifierLength = tokenTypeCounts.getOrDefault("IDENTIFIER", 0) > 0 ?
                    (double) totalIdentifierChars / tokenTypeCounts.get("IDENTIFIER") : 0;

            writer.write("\nLiteral stats:\n");
            writer.write("Total literal bytes: " + totalLiteralBytes + "\n");
            writer.write("Total literal chars: " + totalLiteralChars + "\n");
            writer.write("Average literal chars: " + avgLiteralLength + "\n");
            writer.write("Max literal chars: " + maxLiteralChars + "\n");

            writer.write("\nIdentifier stats:\n");
            writer.write("Total identifier bytes: " + totalIdentifierBytes + "\n");
            writer.write("Total identifier chars: " + totalIdentifierChars + "\n");
            writer.write("Average identifier chars: " + avgIdentifierLength + "\n");
            writer.write("Max identifier chars: " + maxIdentifierChars + "\n");

            writer.write("\nTotal bytes (all tokens): " + totalBytes + "\n");
        }
    }
}
