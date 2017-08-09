package com.datadoghq.trace.writer;

import com.datadoghq.trace.DDBaseSpan;
import com.google.auto.service.AutoService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;

/**
 * This writer write provided traces to the a DD agent which is most of time located on the same
 * host.
 *
 * <p>It handles writes asynchronuously so the calling threads are automatically released. However,
 * if too much spans are collected the writers can reach a state where it is forced to drop incoming
 * spans.
 */
@Slf4j
@AutoService(Writer.class)
public class DDAgentWriter implements Writer {

  /** Default location of the DD agent */
  public static final String DEFAULT_HOSTNAME = "localhost";

  public static final int DEFAULT_PORT = 8126;

  /** Maximum number of traces kept in memory */
  private static final int DEFAULT_MAX_TRACES = 1000;

  /** Timeout for the API in seconds */
  private static final long API_TIMEOUT_SECONDS = 2;

  /** Flush interval for the API in seconds */
  private static final long FLUSH_TIME_SECONDS = 5;

  /** Scheduled thread pool, it' acting like a cron */
  private final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(1);
  /** Effective thread pool, where real logic is done */
  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  /** The DD agent api */
  private final DDApi api;
  /** In memory collection of traces waiting for departure */
  private final WriterQueue<List<DDBaseSpan<?>>> traces;

  public DDAgentWriter() {
    this(new DDApi(DEFAULT_HOSTNAME, DEFAULT_PORT));
  }

  public DDAgentWriter(final DDApi api) {
    super();
    this.api = api;
    traces = new WriterQueue<>(DEFAULT_MAX_TRACES);
  }

  /* (non-Javadoc)
   * @see com.datadoghq.trace.Writer#write(java.util.List)
   */
  @Override
  public void write(final List<DDBaseSpan<?>> trace) {

    final List<DDBaseSpan<?>> removed = traces.add(trace);
    if (removed != null) {
      log.warn("Queue is full, dropping one trace, queue size: {}", DEFAULT_MAX_TRACES);
    }
  }

  /* (non-Javadoc)
   * @see com.datadoghq.trace.writer.Writer#start()
   */
  @Override
  public void start() {
    scheduledExecutor.scheduleAtFixedRate(
        new TracesSendingTask(), 0, FLUSH_TIME_SECONDS, TimeUnit.SECONDS);
  }

  /* (non-Javadoc)
   * @see com.datadoghq.trace.Writer#close()
   */
  @Override
  public void close() {
    scheduledExecutor.shutdownNow();
    executor.shutdownNow();
    try {
      scheduledExecutor.awaitTermination(500, TimeUnit.MILLISECONDS);
    } catch (final InterruptedException e) {
      log.info("Writer properly closed and async writer interrupted.");
    }

    try {
      executor.awaitTermination(500, TimeUnit.MILLISECONDS);
    } catch (final InterruptedException e) {
      log.info("Writer properly closed and async writer interrupted.");
    }
  }

  static class WriterQueue<T> {

    private final LinkedList<T> list;
    private final int capacity;
    private final Lock lock = new ReentrantLock();
    private int nbElements = 0;

    public WriterQueue(final int capacity) {
      if (capacity < 1) {
        throw new IllegalArgumentException("Capacity couldn't be 0");
      }
      list = new LinkedList<>();
      this.capacity = capacity;
    }

    public int size() {
      return nbElements;
    }

    public int drainTo(final Collection<T> c) {
      lock.lock();
      int i = 0;
      final int n = nbElements;
      try {
        while (i < n) {
          final T element = list.getLast();
          c.add(element); // things can go wrong here
          list.removeLast();
          ++i;
          --nbElements;
        }
      } catch (final Throwable ex) {
        log.warn("Unexpected error while draining the queue: {}", ex.getMessage());
        throw ex;
      } finally {
        // Recover the nominal state
        nbElements = list.size();
        lock.unlock();
      }
      return i;
    }

    public T add(final T element) {

      lock.lock();
      T removed = null;
      try {
        if (nbElements < capacity) {
          list.addFirst(element);
          ++nbElements;
        } else {
          removed = removeAndAdd(element);
        }
      } finally {
        lock.unlock();
      }
      return removed;
    }

    public boolean isEmpty() {
      return nbElements == 0;
    }

    private T removeAndAdd(final T element) {
      final int index = ThreadLocalRandom.current().nextInt(0, nbElements);
      final T removed = list.remove(index);
      list.addFirst(element);
      return removed;
    }
  }

  /** Infinite tasks blocking until some spans come in the blocking queue. */
  private class TracesSendingTask implements Runnable {

    @Override
    public void run() {
      final Future<Long> future = executor.submit(new SendingTask());
      try {
        final long nbTraces = future.get(API_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        log.debug("Successfully sending {} traces to the API", nbTraces);
      } catch (final TimeoutException e) {
        log.debug("Timeout! Fail to send traces to the API: {}", e.getMessage());
      } catch (final Throwable e) {
        log.debug("Fail to send traces to the API: {}", e.getMessage());
      }
    }

    public void size() {}

    class SendingTask implements Callable<Long> {

      @Override
      public Long call() throws Exception {
        if (traces.isEmpty()) {
          return 0L;
        }

        final List<List<DDBaseSpan<?>>> payload = new ArrayList<>();
        int nbTraces = traces.drainTo(payload);

        int nbSpans = 0;
        for (final List<?> trace : payload) {
          nbTraces++;
          nbSpans += trace.size();
        }

        log.debug("Sending {} traces ({} spans) to the API (async)", nbTraces, nbSpans);
        final boolean isSent = api.sendTraces(payload);

        if (!isSent) {
          log.warn("Failing to send {} traces to the API", nbTraces);
          return 0L;
        }
        return (long) nbTraces;
      }
    }
  }
}
