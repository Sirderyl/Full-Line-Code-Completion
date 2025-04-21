package com.codelm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ParsingText {
    public static void main(String[] args) {
        try {
            String basePath = "src/main/java/com/codelm/taxi/";
            String inputPath = basePath + "TaxiRestService.java";
            String outputPath = basePath + "CleanTaxiRestService.java";

            String javaCode = Files.readString(Paths.get(inputPath));
            Parser parser = new Parser();
            String cleanedCode = parser.cleanJavaCode(javaCode);
            String formattedCode = parser.formatJavaCode(cleanedCode);

            //FIMProcessor processor = new FIMProcessor();
            //String fimCode = processor.splitForFIM(formattedCode);

            Files.writeString(Paths.get(outputPath), formattedCode);

            System.out.println("Cleaned and formatted code written to " + outputPath);
        } catch (IOException e) {
            System.err.println("Error reading the file: " + e.getMessage());
        }
    }
}
