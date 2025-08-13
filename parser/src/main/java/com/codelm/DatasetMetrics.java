package com.codelm;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Measure dataset size and number of files from zip archives
 */
public class DatasetMetrics {
    // Set thread pool size to the number of CPU cores
    private static final int THREAD_POOL_SIZE = Runtime.getRuntime().availableProcessors();

    public static void main(String[] args) throws IOException {
        Instant start = Instant.now();

        String dirPath = "../data/test";
        boolean filterGarbage = true;

        Path dir = Paths.get(dirPath);
        List<Path> zipPaths;
        try (Stream<Path> stream = Files.list(dir)) {
            zipPaths = stream.sorted().toList();
        }

        // Thread safe counters
        AtomicLong count = new AtomicLong(0);
        AtomicLong totalSize = new AtomicLong(0);

        // Create a thread pool
        try (ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE)) {
            for (Path zipPath : zipPaths) {
                executor.submit(() -> {
                    try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
                        for (ZipEntry entry : Collections.list(zipFile.entries())) {
                            try {
                                byte[] bytes = zipFile.getInputStream(entry).readAllBytes();
                                String content = new String(bytes, StandardCharsets.UTF_8);
                                if (filterGarbage && GarbageFileFilter.isGarbage(entry.getName(), content)) {
                                    System.out.println(entry.getName() + " " + zipFile);
                                } else {
                                    count.incrementAndGet();
                                    totalSize.addAndGet(bytes.length);
                                }
                            } catch (Exception e) {
                                System.err.println("Error processing file " + entry.getName() + " in " + zipPath + ": " + e.getMessage());
                            }
                        }
                        System.out.println("Processed " + count + " entries in " + zipFile + ".");
                    } catch (Exception e) {
                        System.err.println("Error processing " + zipPath + ": " + e.getMessage());
                    }
                });
            }
        }

        Instant end = Instant.now();
        long duration = Duration.between(start, end).toMinutes();

        System.out.println("\nTotal Java files: " + count.get());
        System.out.println("Total size: " + totalSize.get() + " MB");
        Files.writeString(Paths.get(dirPath, "dataset_metrics_filter.txt"), "Total Java files: " +
                count.get() + "\n" + "Total size: " + totalSize.get() + " MB" + "\n" + "It took " + duration + " mins");
    }
}
