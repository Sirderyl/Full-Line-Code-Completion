package uk.ac.newcastle.enterprisemiddleware;

import java.util.Arrays;
import java.util.Random;

public class FIMProcessor {

    public FIMProcessor() {
    }

    public String splitForFIM(String javaCode) {
        // Split the code into lines
        String[] lines = javaCode.split("\n");
        int totalLines = lines.length;

        // If too small to split meaningfully, return the original code
        if (totalLines < 3) {
            System.out.println("File too short, returning original code");
            return javaCode;
        }

        Random rand = new Random();
        // Choose split point between 25% and 75% of the code
        int splitIndex = rand.nextInt(totalLines / 2) + totalLines / 4;
        // Middle length is up to a quarter of the code, but not exceeding remaining lines
        int middleLength = Math.min(rand.nextInt(totalLines / 4), totalLines - splitIndex);

        // Build prefix, middle, and suffix from line arrays
        String prefix = String.join("\n", Arrays.copyOfRange(lines, 0, splitIndex));
        String middle = String.join("\n", Arrays.copyOfRange(lines, splitIndex, splitIndex + middleLength));
        String suffix = String.join("\n", Arrays.copyOfRange(lines, splitIndex + middleLength, totalLines));

        // Combine with sentinel tokens into a single string
        return "<PRE>" + prefix + "<MID>" + middle + "<SUF>" + suffix;
    }
}
