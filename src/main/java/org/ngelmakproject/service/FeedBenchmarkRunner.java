package org.ngelmakproject.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class FeedBenchmarkRunner {

    private final PostService postService;

    private static final int ITERATIONS = 300;

    @PostConstruct
    public void runBenchmark() {
        log.info("Starting feed benchmark…");

        benchmark("getFeedV3", () -> postService.getFeedV3(pageable(), "session123"));
        benchmark("getFeedV2", () -> postService.getFeedV2(pageable(), "session123"));
        benchmark("getFeed(sessionKey,pageable)", () -> postService.getFeed("session123", pageable()));
        benchmark("getFeed(pageable)", () -> postService.getFeed(pageable()));

        log.info("Feed benchmark completed.");
    }

    private Pageable pageable() {
        return PageRequest.of(0, 50);
    }

    private void benchmark(String name, Runnable method) {
        List<Long> latencies = new ArrayList<>(ITERATIONS);

        // Warm‑up
        for (int i = 0; i < 20; i++) method.run();

        // Measure
        for (int i = 0; i < ITERATIONS; i++) {
            long start = System.nanoTime();
            method.run();
            long end = System.nanoTime();
            latencies.add(end - start);
        }

        printStats(name, latencies);
    }

    private void printStats(String name, List<Long> latencies) {
        Collections.sort(latencies);

        long min = latencies.get(0);
        long max = latencies.get(latencies.size() - 1);

        double mean = latencies.stream().mapToLong(v -> v).average().orElse(0);

        double variance = latencies.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .sum() / latencies.size();

        double stdDev = Math.sqrt(variance);

        long p50 = percentile(latencies, 0.50);
        long p95 = percentile(latencies, 0.95);
        long p99 = percentile(latencies, 0.99);

        log.info("\n=== {} ===\n" +
                        "Iterations: {}\n" +
                        "Min:  {}\n" +
                        "Max:  {}\n" +
                        "Mean: {}\n" +
                        "Std:  {}\n" +
                        "p50:  {}\n" +
                        "p95:  {}\n" +
                        "p99:  {}",
                name,
                latencies.size(),
                nanos(min),
                nanos(max),
                nanos((long) mean),
                nanos((long) stdDev),
                nanos(p50),
                nanos(p95),
                nanos(p99)
        );
    }

    private long percentile(List<Long> sorted, double percentile) {
        int index = (int) Math.ceil(percentile * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    private String nanos(long ns) {
        return TimeUnit.NANOSECONDS.toMicros(ns) + " µs";
    }
}

