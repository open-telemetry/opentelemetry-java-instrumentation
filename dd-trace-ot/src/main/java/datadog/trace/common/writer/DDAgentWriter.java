package datadog.trace.common.writer;

import static datadog.trace.api.Config.DEFAULT_AGENT_HOST;
import static datadog.trace.api.Config.DEFAULT_AGENT_UNIX_DOMAIN_SOCKET;
import static datadog.trace.api.Config.DEFAULT_TRACE_AGENT_PORT;

import datadog.opentracing.DDSpan;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/**
 * This writer write provided traces to the a DD agent which is most of time located on the same
 * host.
 *
 * <p>
 *
 * <p>It handles writes asynchronuously so the calling threads are automatically released. However,
 * if too much spans are collected the writers can reach a state where it is forced to drop incoming
 * spans.
 */
@Slf4j
public class DDAgentWriter implements Writer {

  /** Maximum number of traces kept in memory */
  static final int DEFAULT_MAX_TRACES = 7000;

  /** Flush interval for the API in seconds */
  static final long FLUSH_TIME_SECONDS = 1;

  private final ThreadFactory agentWriterThreadFactory =
      new ThreadFactory() {
        @Override
        public Thread newThread(final Runnable r) {
          final Thread thread = new Thread(r, "dd-agent-writer");
          thread.setDaemon(true);
          return thread;
        }
      };

  /** Scheduled thread pool, acting like a cron */
  private final ScheduledExecutorService scheduledExecutor =
      Executors.newScheduledThreadPool(1, agentWriterThreadFactory);

  /** The DD agent api */
  private final DDApi api;

  /** In memory collection of traces waiting for departure */
  private final WriterQueue<List<DDSpan>> traces;

  private boolean queueFullReported = false;

  public DDAgentWriter() {
    this(new DDApi(DEFAULT_AGENT_HOST, DEFAULT_TRACE_AGENT_PORT, DEFAULT_AGENT_UNIX_DOMAIN_SOCKET));
  }

  public DDAgentWriter(final DDApi api) {
    this(api, new WriterQueue<List<DDSpan>>(DEFAULT_MAX_TRACES));
  }

  public DDAgentWriter(final DDApi api, final WriterQueue<List<DDSpan>> queue) {
    super();
    this.api = api;
    traces = queue;
  }

  /* (non-Javadoc)
   * @see datadog.trace.Writer#write(java.util.List)
   */
  @Override
  public void write(final List<DDSpan> trace) {
    final List<DDSpan> removed = traces.add(trace);
    if (removed != null && !queueFullReported) {
      log.debug("Queue is full, traces will be discarded, queue size: {}", DEFAULT_MAX_TRACES);
      queueFullReported = true;
      return;
    }
    queueFullReported = false;
  }

  /* (non-Javadoc)
   * @see Writer#start()
   */
  @Override
  public void start() {
    scheduledExecutor.scheduleAtFixedRate(
        new TracesSendingTask(), 0, FLUSH_TIME_SECONDS, TimeUnit.SECONDS);
  }

  /* (non-Javadoc)
   * @see datadog.trace.Writer#close()
   */
  @Override
  public void close() {
    scheduledExecutor.shutdownNow();
    try {
      scheduledExecutor.awaitTermination(500, TimeUnit.MILLISECONDS);
    } catch (final InterruptedException e) {
      log.info("Writer properly closed and async writer interrupted.");
    }
  }

  @Override
  public String toString() {
    return "DDAgentWriter { api=" + api + " }";
  }

  public DDApi getApi() {
    return api;
  }

  class TracesSendingTask implements Runnable {
    @Override
    public void run() {
      try {
        if (traces.isEmpty()) {
          return;
        }

        final List<List<DDSpan>> payload = traces.getAll();

        if (log.isDebugEnabled()) {
          int nbSpans = 0;
          for (final List<?> trace : payload) {
            nbSpans += trace.size();
          }
          log.debug("Sending {} traces ({} spans) to the API (async)", payload.size(), nbSpans);
        }

        final boolean isSent = api.sendTraces(payload);
        if (isSent) {
          log.debug("Successfully sent {} traces to the API", payload.size());
        } else {
          log.debug("Failed to send {} traces to the API", payload.size());
        }
      } catch (final Throwable e) {
        log.debug("Failed to send traces to the API: {}", e.getMessage());
      }
    }
  }
}
