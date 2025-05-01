package com.codelm;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class ParsingText {
    private static final String INPUT_DIR = "../data/extracted_java_zip";
    private static final String OUTPUT_DIR = "../data/cleaned_java_zip";
    private static final Parser parser = new Parser();

    public static void main(String[] args) throws IOException {
        Path inputPath = Paths.get(INPUT_DIR);
        Path outputPath = Paths.get(OUTPUT_DIR);

        // Get the list of zip files
        List<Path> zipPaths;
        try (Stream<Path> stream = Files.list(inputPath)) {
            zipPaths = stream.sorted().toList();
        }

        // Iterate through all the zip files in the dataset
        for (Path zipPath : zipPaths) {
            String zipName = zipPath.getFileName().toString();
            Path outputZipPath = outputPath.resolve("processed_" + zipName);

            try (ZipFile zipFile = new ZipFile(zipPath.toFile());
                 ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outputZipPath.toFile()))) {

                for (ZipEntry entry : Collections.list(zipFile.entries())) {
                    if (entry.getName().endsWith(".java")) {
                        String fileName = entry.getName();
                        try {
                            String content = new String(zipFile.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
                            if (!GarbageFileFilter.isGarbage(fileName, content)) {
                                String processedContent = parser.cleanJavaCode(content);
                                String formattedCode = parser.formatJavaCode(processedContent);
                                zos.putNextEntry(new ZipEntry(fileName));
                                zos.write(formattedCode.getBytes(StandardCharsets.UTF_8));
                                zos.closeEntry();
                            }
                        } catch (Exception e) {
                            System.err.println("Error processing file " + fileName + " in " + zipName + ": " + e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Error processing " + zipName + ": " + e.getMessage());
            }
        }
    }
}
