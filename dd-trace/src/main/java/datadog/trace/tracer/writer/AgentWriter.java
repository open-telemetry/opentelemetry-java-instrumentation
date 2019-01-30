package datadog.trace.tracer.writer;

import datadog.trace.tracer.Trace;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AgentWriter implements Writer {

  /** Maximum number of traces kept in memory */
  static final int DEFAULT_QUEUE_SIZE = 7000;
  /** Flush interval for the API in seconds */
  static final long FLUSH_TIME_SECONDS = 1;
  /** Maximum amount of time to await for scheduler to shutdown */
  static final long SHUTDOWN_TIMEOUT_SECONDS = 1;

  private static final ThreadFactory THREAD_FACTORY =
      new ThreadFactory() {
        @Override
        public Thread newThread(final Runnable r) {
          final Thread thread = new Thread(r, "dd-agent-writer");
          thread.setDaemon(true);
          return thread;
        }
      };

  /** Scheduled thread pool, acting like a cron */
  private final ScheduledExecutorService executorService =
      Executors.newScheduledThreadPool(1, THREAD_FACTORY);

  private final TracesSendingTask task;
  private final ShutdownCallback shutdownCallback;

  public AgentWriter(final AgentClient client) {
    this(client, DEFAULT_QUEUE_SIZE);
  }

  AgentWriter(final AgentClient client, final int queueSize) {
    task = new TracesSendingTask(client, queueSize);
    shutdownCallback = new ShutdownCallback(executorService);
  }

  /** @return Datadog agent URL. Visible for testing. */
  URL getAgentUrl() {
    return task.getClient().getAgentUrl();
  }

  @Override
  public void write(final Trace trace) {
    if (trace.isValid()) {
      if (!task.getQueue().offer(trace)) {
        log.debug("Writer queue is full, dropping trace {}", trace);
      }
    }
  }

  @Override
  public void incrementTraceCount() {
    task.getTraceCount().incrementAndGet();
  }

  @Override
  public SampleRateByService getSampleRateByService() {
    return task.getSampleRateByService().get();
  }

  @Override
  public void start() {
    executorService.scheduleAtFixedRate(task, 0, FLUSH_TIME_SECONDS, TimeUnit.SECONDS);
    try {
      Runtime.getRuntime().addShutdownHook(shutdownCallback);
    } catch (final IllegalStateException ex) {
      // The JVM is already shutting down.
    }
  }

  @Override
  public void close() {
    // Perform actions needed to shutdown this writer
    shutdownCallback.run();
  }

  @Override
  public void finalize() {
    close();
  }

  /** Infinite tasks blocking until some spans come in the queue. */
  private static final class TracesSendingTask implements Runnable {

    /** The Datadog agent client */
    @Getter private final AgentClient client;
    /** Queue size */
    private final int queueSize;
    /** In memory collection of traces waiting for departure */
    @Getter private final BlockingQueue<Trace> queue;
    /** Number of traces to be written */
    @Getter private final AtomicInteger traceCount = new AtomicInteger(0);
    /** Sample rate by service returned by Datadog agent */
    @Getter
    private final AtomicReference<SampleRateByService> sampleRateByService =
        new AtomicReference<>(SampleRateByService.EMPTY_INSTANCE);

    TracesSendingTask(final AgentClient client, final int queueSize) {
      this.client = client;
      this.queueSize = queueSize;
      queue = new ArrayBlockingQueue<>(queueSize);
    }

    @Override
    public void run() {
      try {
        final List<Trace> tracesToWrite = new ArrayList<>(queueSize);
        queue.drainTo(tracesToWrite);
        if (tracesToWrite.size() > 0) {
          sampleRateByService.set(client.sendTraces(tracesToWrite, traceCount.getAndSet(0)));
        }
      } catch (final Throwable e) {
        log.debug("Failed to send traces to the API: {}", e.getMessage());
      }
    }
  }

  /**
   * Helper to handle shutting down of the Writer because JVM is shutting down or Writer is closed.
   */
  // Visible for testing
  static final class ShutdownCallback extends Thread {

    private final ExecutorService executorService;

    public ShutdownCallback(final ExecutorService executorService) {
      this.executorService = executorService;
    }

    @Override
    public void run() {
      // We use this logic in two cases:
      // * When JVM is shutting down
      // * When Writer is closed manually/via GC
      // In latter case we need to remove shutdown hook.
      try {
        Runtime.getRuntime().removeShutdownHook(this);
      } catch (final IllegalStateException ex) {
        // The JVM may be shutting down.
      }

      try {
        executorService.shutdownNow();
        executorService.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      } catch (final InterruptedException e) {
        log.info("Writer properly closed and async writer interrupted.");
      }
    }
  }
}
