package org.ngelmakproject.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class FeedBenchmarkRunner {

    private final PostService postService;

    private static final int ITERATIONS = 300;

    private final Map<String, Stats> results = new LinkedHashMap<>();

    @EventListener(ApplicationReadyEvent.class)
    public void runBenchmark() {
        log.info("Starting feed benchmark…");

        run("getFeedV3", () -> postService.getFeedV3(pageable(), "session123"));
        run("getFeedV2", () -> postService.getFeedV2(pageable(), "session123"));
        run("getFeed(sessionKey,pageable)", () -> postService.getFeed("session123", pageable()));
        run("getFeed(pageable)", () -> postService.getFeed(pageable()));

        printFinalSummary();
    }

    private Pageable pageable() {
        return PageRequest.of(0, 20);
    }

    private void run(String name, Runnable method) {
        List<Long> latencies = new ArrayList<>(ITERATIONS);

        // Warm-up
        for (int i = 0; i < 20; i++) method.run();

        // Measure
        for (int i = 0; i < ITERATIONS; i++) {
            long start = System.nanoTime();
            method.run();
            long end = System.nanoTime();
            latencies.add(end - start);
        }

        results.put(name, computeStats(latencies));
    }

    private Stats computeStats(List<Long> latencies) {
        Collections.sort(latencies);

        long min = latencies.get(0);
        long max = latencies.get(latencies.size() - 1);

        double mean = latencies.stream().mapToLong(v -> v).average().orElse(0);

        double variance = latencies.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .sum() / latencies.size();

        double stdDev = Math.sqrt(variance);

        return new Stats(
                min,
                max,
                (long) mean,
                (long) stdDev,
                percentile(latencies, 0.50),
                percentile(latencies, 0.95),
                percentile(latencies, 0.99)
        );
    }

    private long percentile(List<Long> sorted, double percentile) {
        int index = (int) Math.ceil(percentile * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    private void printFinalSummary() {
        log.info("\n\n==================== FEED BENCHMARK SUMMARY ====================\n");

        results.forEach((name, s) -> {
            log.info("""
                    {}:
                      min:  {} µs
                      max:  {} µs
                      mean: {} µs
                      std:  {} µs
                      p50:  {} µs
                      p95:  {} µs
                      p99:  {} µs
                    """,
                    name,
                    toMicros(s.min),
                    toMicros(s.max),
                    toMicros(s.mean),
                    toMicros(s.std),
                    toMicros(s.p50),
                    toMicros(s.p95),
                    toMicros(s.p99)
            );
        });

        log.info("===============================================================\n");
    }

    private long toMicros(long ns) {
        return TimeUnit.NANOSECONDS.toMicros(ns);
    }

    private record Stats(long min, long max, long mean, long std, long p50, long p95, long p99) {}
}
