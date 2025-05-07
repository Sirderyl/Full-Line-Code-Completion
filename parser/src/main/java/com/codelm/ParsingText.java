package com.codelm;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class ParsingText {
    private static final String INPUT_DIR = "../data/test1";
    private static final String OUTPUT_DIR = "../data/cleaned_java_zip";
    private static final String STATS_FILE = "../data/analysis_output/token_stats.txt";
    private static final String LITERALS_LOG = "../data/analysis_output/literals.log";
    private static final String IDENTIFIERS_LOG = "../data/analysis_output/identifiers.log";
    private static final Parser parser = new Parser();

    // Set thread pool size to the number of CPU cores
    private static final int THREAD_POOL_SIZE = Runtime.getRuntime().availableProcessors();

    public static void main(String[] args) throws IOException {
        Instant start = Instant.now();

        Path inputPath = Paths.get(INPUT_DIR);
        Path outputPath = Paths.get(OUTPUT_DIR);
        CumulativeTokenStats cStats = new CumulativeTokenStats();

        // Clear log files at the start
        Files.write(Paths.get(LITERALS_LOG), new ArrayList<>(), StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);
        Files.write(Paths.get(IDENTIFIERS_LOG), new ArrayList<>(), StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);

        // Get the list of zip files
        List<Path> zipPaths;
        try (Stream<Path> stream = Files.list(inputPath)) {
            zipPaths = stream.sorted().toList();
        }

        int totalZips = zipPaths.size();
        int processedZips = 0;

        // Create a thread pool
        try (ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE)) {
            // Iterate through all the zip files in the dataset
            for (Path zipPath : zipPaths) {
                String zipName = zipPath.getFileName().toString();
                Path outputZipPath = outputPath.resolve("processed_" + zipName);

                try (ZipFile zipFile = new ZipFile(zipPath.toFile());
                     ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outputZipPath.toFile()))) {

                    List<Callable<Result>> tasks = new ArrayList<>();
                    for (ZipEntry entry : Collections.list(zipFile.entries())) {
                        tasks.add(() -> {
                            try {
                                String content = new String(zipFile.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
                                if (!GarbageFileFilter.isGarbage(entry.getName(), content)) {
                                    String processedContent = parser.cleanJavaCode(content);
                                    String formattedCode = parser.formatJavaCode(processedContent);

                                    // Analyze token on the preprocessed code
                                    TokenAnalyzer.TokenStats stats = TokenAnalyzer.analyze(formattedCode);

                                    return new Result(entry.getName(), formattedCode, stats);
                                }
                            } catch (Exception e) {
                                //System.err.println("Error processing file " + entry.getName() + " in " + zipName + ": " + e.getMessage());
                            }

                            return null;
                        });
                    }

                    // Execute tasks in parallel
                    List<Future<Result>> futures = executor.invokeAll(tasks);

                    // Process results sequentially
                    for (Future<Result> future : futures) {
                        Result result = future.get();
                        if (result != null) {
                            zos.putNextEntry(new ZipEntry(result.fileName));
                            zos.write(result.formattedCode.getBytes(StandardCharsets.UTF_8));
                            zos.closeEntry();

                            if (result.stats != null) {
                                cStats.update(result.stats);
                                appendToFile(LITERALS_LOG, result.stats.literalValues);
                                appendToFile(IDENTIFIERS_LOG, result.stats.identifierValues);
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error processing " + zipName + ": " + e.getMessage());
                }

                // Progress update
                processedZips++;
                updateProgress(processedZips, totalZips, start);
            }
        }

        cStats.writeStatsToFile(STATS_FILE);

        Instant end = Instant.now();
        long duration = Duration.between(start, end).toMinutes();

        Files.writeString(Paths.get(STATS_FILE), "\nIt took " + duration + " minutes", StandardOpenOption.APPEND);
    }

    private static void appendToFile(String filePath, List<String> values) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(filePath), StandardOpenOption.APPEND)) {
            for (String value : values) {
                writer.write(value);
                writer.newLine();
            }
        }
    }

    private static void updateProgress(int processed, int total, Instant start) {
        int percent = (int) ((double) processed / total * 100);
        long elapsed = Duration.between(start, Instant.now()).toMillis();
        long estimatedTotal = (long) ((double) elapsed / processed * total);
        long remaining = estimatedTotal - elapsed;

        String bar = "[" + "=".repeat(percent / 2) + " ".repeat(50 - percent / 2) + "]";
        System.out.printf("\r%s %d%% complete | Processed zips: %d | Elapsed: %s | Remaining: %s",
                bar, percent, processed, formatTime(elapsed), formatTime(remaining));
    }

    private static String formatTime(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        seconds %= 60;
        return String.format("%dm %ds", minutes, seconds);
    }

    // Helper class to hold the result of processing each file (for thread safety)
    private static class Result {
        String fileName;
        String formattedCode;
        TokenAnalyzer.TokenStats stats;

        public Result(String fileName, String formattedCode, TokenAnalyzer.TokenStats stats) {
            this.fileName = fileName;
            this.formattedCode = formattedCode;
            this.stats = stats;
        }
    }
}
