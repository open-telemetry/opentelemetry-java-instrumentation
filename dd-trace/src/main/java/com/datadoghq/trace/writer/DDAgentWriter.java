package com.datadoghq.trace.writer;

import com.datadoghq.trace.DDBaseSpan;
import com.google.auto.service.AutoService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
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

  /** Maximum number of spans kept in memory */
  private static final int DEFAULT_MAX_SPANS = 1000;

  /** Maximum number of traces sent to the DD agent API at once */
  private static final int DEFAULT_BATCH_SIZE = 10;

  /**
   * Used to ensure that we don't keep too many spans (while the blocking queue collect traces...)
   */
  private final Semaphore tokens;

  /** In memory collection of traces waiting for departure */
  private final BlockingQueue<List<DDBaseSpan<?>>> traces;

  /** Async worker that posts the spans to the DD agent */
  private final ExecutorService executor = Executors.newSingleThreadExecutor();

  /** The DD agent api */
  private final DDApi api;

  public DDAgentWriter() {
    this(new DDApi(DEFAULT_HOSTNAME, DEFAULT_PORT));
  }

  public DDAgentWriter(final DDApi api) {
    super();
    this.api = api;

    tokens = new Semaphore(DEFAULT_MAX_SPANS);
    traces = new ArrayBlockingQueue<>(DEFAULT_MAX_SPANS);
  }

  /* (non-Javadoc)
   * @see com.datadoghq.trace.Writer#write(java.util.List)
   */
  @Override
  public void write(final List<DDBaseSpan<?>> trace) {
    //Try to add a new span in the queue
    final boolean proceed = tokens.tryAcquire(trace.size());

    if (proceed) {
      traces.add(trace);
    } else {
      log.warn(
          "Cannot add a trace of {} as the async queue is full. Queue max size: {}",
          trace.size(),
          DEFAULT_MAX_SPANS);
    }
  }

  /* (non-Javadoc)
   * @see com.datadoghq.trace.writer.Writer#start()
   */
  @Override
  public void start() {
    executor.submit(new SpansSendingTask());
  }

  /* (non-Javadoc)
   * @see com.datadoghq.trace.Writer#close()
   */
  @Override
  public void close() {
    executor.shutdownNow();
    try {
      executor.awaitTermination(500, TimeUnit.MILLISECONDS);
    } catch (final InterruptedException e) {
      log.info("Writer properly closed and async writer interrupted.");
    }
  }

  /** Infinite tasks blocking until some spans come in the blocking queue. */
  protected class SpansSendingTask implements Runnable {

    @Override
    public void run() {
      while (true) {
        try {
          final List<List<DDBaseSpan<?>>> payload = new ArrayList<>();

          //WAIT until a new span comes
          final List<DDBaseSpan<?>> l = DDAgentWriter.this.traces.take();
          payload.add(l);

          //Drain all spans up to a certain batch suze
          traces.drainTo(payload, DEFAULT_BATCH_SIZE);

          //SEND the payload to the agent
          log.debug("Async writer about to write {} traces.", payload.size());
          api.sendTraces(payload);

          //Compute the number of spans sent
          int spansCount = 0;
          for (final List<DDBaseSpan<?>> trace : payload) {
            spansCount += trace.size();
          }
          log.debug(
              "Async writer just sent {} spans through {} traces", spansCount, payload.size());

          //Release the tokens
          tokens.release(spansCount);
        } catch (final InterruptedException e) {
          log.info("Async writer interrupted.");

          //The thread was interrupted, we break the LOOP
          break;
        } catch (final Throwable e) {
          log.error("Unexpected error! Some traces may have been dropped.", e);
        }
      }
    }
  }
}
