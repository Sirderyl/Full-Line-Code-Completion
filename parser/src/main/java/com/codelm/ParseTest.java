package com.codelm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ParseTest {
    public static void main(String[] args) {
        try {
            String basePath = "../data/";
            String fileName = "LinkBenchRequest_65.java";
            String inputPath = basePath + fileName;
            String outputPath = basePath + "Cleaned.java";

            String javaCode = Files.readString(Paths.get(inputPath));
            System.out.println(GarbageFileFilter.isGarbage(fileName, javaCode));

            Parser parser = new Parser();
            String cleanedCode = parser.cleanJavaCode(javaCode);
            String formattedCode = parser.formatJavaCode(cleanedCode);

            //FIMProcessor processor = new FIMProcessor();
            //String fimCode = processor.splitForFIM(formattedCode);

            Files.writeString(Paths.get(outputPath), formattedCode);

            System.out.println("Cleaned and formatted code written to " + outputPath);

            String code = "publicvoidrun(){}";
            System.out.println(parser.retokenize(code));
        } catch (IOException e) {
            System.err.println("Error reading the file: " + e.getMessage());
        }
    }
}
