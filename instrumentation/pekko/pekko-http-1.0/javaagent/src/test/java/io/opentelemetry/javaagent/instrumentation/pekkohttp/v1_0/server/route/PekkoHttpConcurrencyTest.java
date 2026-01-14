package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server.route;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

class PekkoHttpConcurrencyTest {

  @Test
  void testConcurrency() throws InterruptedException {
   /* PekkoRouteHolder holder = new PekkoRouteHolder();
    int threadCount = 50;
    int iterations = 100000;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();

    for (int i = 0; i < iterations; i++) {
      executor.submit(() -> {
        try {
          holder.save();
          holder.didNotMatch();
          holder.pushIfNotCompletelyMatched("/test");
          holder.route();
          holder.restore();
        } catch (RuntimeException e) {
          errors.add(e);
        }
      });
    }

    executor.shutdown();
    executor.awaitTermination(30, TimeUnit.SECONDS);
    if (!errors.isEmpty()) {
      for (Throwable t : errors) {
        t.printStackTrace();
      }
    }
    assertTrue(errors.isEmpty(), "Concurrency issue detected! Check logs.");
  }*/
}
