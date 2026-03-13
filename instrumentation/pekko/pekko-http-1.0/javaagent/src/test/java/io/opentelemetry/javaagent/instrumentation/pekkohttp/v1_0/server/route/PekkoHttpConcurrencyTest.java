package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server.route;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.pekko.http.scaladsl.model.Uri;
import org.junit.jupiter.api.Test;

class PekkoHttpConcurrencyTest {

  @Test
  void sharedHolder_shouldCorruptState_underConcurrency() throws InterruptedException {

    PekkoRouteHolder sharedHolder = new PekkoRouteHolder();

    int threadCount = 50;
    int iterations = 10_000;

    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();

    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(iterations);

    for (int i = 0; i < iterations; i++) {
      executor.submit(() -> {
        try {
          start.await();

          String myPath = "/test-" + Thread.currentThread().getId() + "-" + System.nanoTime();
          Uri.Path before = null;
          Uri.Path after = mock(Uri.Path.class);
          sharedHolder.save();
          sharedHolder.push(before, after, myPath);

          String result = sharedHolder.route();
          if (result == null || !result.contains(myPath)) {
            errors.add(new IllegalStateException(
                "Data interleaved. Expected [" + myPath + "] to be in [" + result + "]"));
          }

          sharedHolder.restore();

        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          errors.add(e);
        } catch (Throwable t) {
          errors.add(t);
        } finally {
          done.countDown();
        }
      });
    }

    start.countDown();
    done.await();
    executor.shutdownNow();
    assertTrue(errors.isEmpty(), "Shared holder corrupted! Errors: " + errors);
  }

  @Test
  void perRequestHolder_shouldNotCorruptState_underConcurrency() throws InterruptedException {
    int threadCount = 50;
    int iterations = 10_000;

    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();

    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(iterations);

    for (int i = 0; i < iterations; i++) {
      executor.submit(() -> {
        try {
          start.await();
          PekkoRouteHolder holder = new PekkoRouteHolder();
          String myPath = "/test-" + Thread.currentThread().getId() + "-" + System.nanoTime();

          Uri.Path before = null;
          Uri.Path after = mock(Uri.Path.class);

          holder.save();
          holder.push(before, after, myPath);

          String result = holder.route();
          if (result == null || !result.contains(myPath)) {
            errors.add(new IllegalStateException("Unexpected corruption in per-request holder."));
          }

          holder.restore();

        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          errors.add(e);
        } catch (Throwable t) {
          errors.add(t);
        } finally {
          done.countDown();
        }
      });
    }

    start.countDown();
    done.await();
    executor.shutdownNow();
    assertTrue(errors.isEmpty(), "Per-request holder should never be corrupted.");
  }
}
