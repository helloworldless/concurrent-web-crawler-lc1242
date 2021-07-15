package com.davidagood.concurrency.leetcode1242;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static java.util.Collections.emptyList;
import static java.util.Objects.isNull;
import static java.util.stream.Collectors.toList;

/**
 * LeetCode 1242. Web Crawler Multithreaded
 * https://leetcode.com/problems/web-crawler-multithreaded
 */
class LeetCode1242 {

    public static void main(String[] args) {
        var app = new LeetCode1242();
        String inputResourcePath = "/leet-code-1242-input-large.txt";
        String expectedResultResourcePath = "/leet-code-1242-input-large.expected.txt";
        var problemInputFileParser = new ProblemInputFileParser(inputResourcePath, expectedResultResourcePath);
        var htmlParser = new HtmlParser(problemInputFileParser);

        long startedAt = System.currentTimeMillis();
        List<String> result = app.crawl(htmlParser);
        long finishedAt = System.currentTimeMillis();

        System.out.println("runtime: " + (finishedAt - startedAt) + " ms");
        System.out.println("result length:  " + result.size());
        System.out.println("result: " + result);
        Set<String> expected = htmlParser.getExpectedResult();
        System.out.println("expected length: " + expected.size());
        System.out.println("expected: " + expected);
        // taking a shortcut here using a Set
        System.out.println("result ≈ expected? " + expected.equals(new HashSet<>(result)));
    }

    static String extractDomain(String url) {
        // remove "http://"
        String sub = url.substring(7);
        // get rid of anything after "/", inclusive
        String[] parts = sub.split("/");
        return parts[0];
    }

    public List<String> crawl(HtmlParser htmlParser) {
        String startUrl = htmlParser.getStartUrl();
        Set<String> visited = Collections.synchronizedSet(new HashSet<>());
        visited.add(startUrl);

        String domain = extractDomain(startUrl);

        BlockingQueue<String> bq = new LinkedBlockingQueue<>();
        bq.add(startUrl);

        int n = 5;
        List<CrawlerThread> threads = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            var t = new CrawlerThread(visited, htmlParser, bq, domain);
            threads.add(t);
        }

        for (CrawlerThread thread : threads) {
            thread.start();
        }

        for (CrawlerThread t : threads) {
            try {
                System.out.println("joining " + t.getName());
                t.join();
            } catch (InterruptedException e) {
                throw new RuntimeException("join interrupted: " + e);
            }
        }

        System.out.println("completed");
        return new ArrayList<>(visited);
    }

    static class CrawlerThread extends Thread {

        private final Set<String> visited;
        private final HtmlParser htmlParser;
        private final BlockingQueue<String> blockingQueue;
        private final String targetDomain;

        CrawlerThread(Set<String> visited, HtmlParser htmlParser, BlockingQueue<String> blockingQueue, String targetDomain) {
            this.visited = visited;
            this.htmlParser = htmlParser;
            this.blockingQueue = blockingQueue;
            this.targetDomain = targetDomain;
        }

        @Override
        public void run() {
            while (true) {
                try {
                    String url = blockingQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (isNull(url)) {
                        System.out.println(Thread.currentThread().getName() + " found no more work");
                        break;
                    }
                    List<String> subUrls = htmlParser.getUrls(url);
                    for (String subUrl : subUrls) {
                        if (!visited.contains(subUrl) && targetDomain.equals(extractDomain(subUrl))) {
                            visited.add(subUrl);
                            blockingQueue.add(subUrl);
                        }
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(Thread.currentThread().getName() + " interrupted; Error: " + e);
                }
            }
        }
    }

    static class ProblemInputFileParser {
        final String startUrl;
        final List<String> urls;
        final Set<String> expectedResult;
        final Map<Integer, List<Integer>> edgesMap = new HashMap<>();

        ProblemInputFileParser(String inputResourcePath, String expectedResultResourcePath) {
            List<String> lines = readLinesFromResource(inputResourcePath);
            List<String> expectedLines = readLinesFromResource(expectedResultResourcePath);
            String expectedRaw = expectedLines.get(0);
            expectedResult = new HashSet<>(Arrays.asList(expectedRaw.substring(1, expectedRaw.length() - 1).split(",")));

            if (lines.size() != 3) {
                throw new RuntimeException("Expected input to be exactly three lines but it had size=" + lines.size());
            }

            // Line 1 is a list of all the URLs
            String urlsLine = lines.get(0);
            String urlCsvs = urlsLine.substring(1, urlsLine.length() - 1);
            String[] urls = urlCsvs.split(",");
            this.urls = Arrays.asList(urls);

            // Line 2 is an adjacency list describing the graph of URLs
            String adjacencyListLine = lines.get(1);

            // If the input is like this "[[1,2],[3,4]]", make it like this "1,2],[3,4" to prepare for the
            // regex split on the following line
            String adjacencyListCsvs = adjacencyListLine.substring(2, adjacencyListLine.length() - 2);
            String[] adjacencyListPairs = adjacencyListCsvs.split("],\\[");

            int[][] adjacencyList = new int[adjacencyListPairs.length][2];

            for (int i = 0; i < adjacencyListPairs.length; i++) {
                String adjacencyListPair = adjacencyListPairs[i];
                String[] values = adjacencyListPair.split(",");
                int from = Integer.parseInt(values[0]);
                int to = Integer.parseInt(values[1]);
                adjacencyList[i] = new int[]{from, to};
            }

            for (int[] edge : adjacencyList) {
                int from = edge[0];
                int to = edge[1];

                if (!edgesMap.containsKey(from)) {
                    edgesMap.put(from, new ArrayList<>());
                }

                edgesMap.get(from).add(to);
            }

            // Line 3 is the starting URL
            this.startUrl = lines.get(2);
        }

        private List<String> readLinesFromResource(String resourcePath) {
            URL resource = this.getClass().getResource(resourcePath);
            List<String> lines;
            try {
                lines = Files.readAllLines(Path.of(resource.toURI()));
            } catch (Exception e) {
                throw new RuntimeException("Failed to read lines from file; Error: " + e);

            }
            return lines;
        }
    }

    static class HtmlParser {

        final ProblemInputFileParser problemInputFileParser;

        HtmlParser(ProblemInputFileParser problemInputFileParser) {
            this.problemInputFileParser = problemInputFileParser;
        }

        Set<String> getExpectedResult() {
            return this.problemInputFileParser.expectedResult;
        }

        String getStartUrl() {
            return this.problemInputFileParser.startUrl;
        }

        List<String> getUrls(String url) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                throw new RuntimeException("interrupted " + Thread.currentThread().getName());
            }
            List<String> urls = this.problemInputFileParser.urls;
            int i = urls.indexOf(url);
            return this.problemInputFileParser.edgesMap.getOrDefault(i, emptyList()).stream().map(urls::get).collect(toList());
        }
    }
}


