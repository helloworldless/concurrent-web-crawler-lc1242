# LeetCode Problem #1242: Web Crawler Multithreaded

Fully self-contained, runnable code 
based on this problem: https://leetcode.com/problems/web-crawler-multithreaded

## Code Runnable on LeetCode

```java
/**
 * // This is the HtmlParser's API interface.
 * // You should not implement it, or speculate about its implementation
 * interface HtmlParser {
 *     public List<String> getUrls(String url) {}
 * }
 */
class Solution {
    
    public List<String> crawl(String startUrl, HtmlParser htmlParser) {
        Set<String> visited = Collections.synchronizedSet(new HashSet<>());
        visited.add(startUrl);
        
        String domain = Util.extractDomain(startUrl);
        
        BlockingQueue<String> bq = new LinkedBlockingQueue<>();
        bq.add(startUrl);
        
        int n = 5;
        List<CrawlerThread> threads = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            var t = new CrawlerThread(visited, htmlParser, bq, domain);
            threads.add(t);
        }
        for (var thread : threads) {
            thread.start();
        }

        for (var t : threads) {
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
    
    
}


class Util {
    static String extractDomain(String url) {
        // remove "http://""
        String sub = url.substring(7);
        // split at the first "/"
        String[] parts = sub.split("/");
        return parts[0];
    }
}

class CrawlerThread extends Thread {
    
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
                if (url == null) {
                    System.out.println(Thread.currentThread().getName() + " found no more work");
                    break;
                }
                List<String> subUrls = htmlParser.getUrls(url);
                for (String subUrl : subUrls) {
                    if (!visited.contains(subUrl) && targetDomain.equals(Util.extractDomain(subUrl))) {
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
```