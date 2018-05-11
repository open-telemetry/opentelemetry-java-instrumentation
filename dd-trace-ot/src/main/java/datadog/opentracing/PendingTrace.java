package datadog.opentracing;

import datadog.opentracing.scopemanager.ContinuableScope;
import java.io.Closeable;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PendingTrace extends ConcurrentLinkedDeque<DDSpan> {
  private static final SpanCleaner SPAN_CLEANER;

  static {
    SPAN_CLEANER = new SpanCleaner();
    SPAN_CLEANER.start();
  }

  private final DDTracer tracer;
  private final long traceId;

  private final ReferenceQueue referenceQueue = new ReferenceQueue();
  private final Set<WeakReference<?>> weakReferences =
      Collections.newSetFromMap(new ConcurrentHashMap<WeakReference<?>, Boolean>());

  private final AtomicInteger pendingReferenceCount = new AtomicInteger(0);

  /** Ensure a trace is never written multiple times */
  private final AtomicBoolean isWritten = new AtomicBoolean(false);

  PendingTrace(final DDTracer tracer, final long traceId) {
    this.tracer = tracer;
    this.traceId = traceId;
    SPAN_CLEANER.pendingTraces.add(this);
  }

  public void registerSpan(final DDSpan span) {
    if (span.context().getTraceId() != traceId) {
      log.debug("{} - span registered for wrong trace ({})", span, traceId);
      return;
    }
    synchronized (span) {
      if (null == span.ref) {
        span.ref = new WeakReference<DDSpan>(span, referenceQueue);
        weakReferences.add(span.ref);
        final int count = pendingReferenceCount.incrementAndGet();
        log.debug("traceId: {} -- registered span {}. count = {}", traceId, span, count);
      } else {
        log.debug("span {} already registered in trace {}", span, traceId);
      }
    }
  }

  private void expireSpan(final DDSpan span) {
    if (span.context().getTraceId() != traceId) {
      log.debug("{} - span expired for wrong trace ({})", span, traceId);
      return;
    }
    synchronized (span) {
      if (null == span.ref) {
        log.debug("span {} not registered in trace {}", span, traceId);
      } else {
        weakReferences.remove(span.ref);
        span.ref.clear();
        span.ref = null;
        expireReference();
      }
    }
  }

  public void addSpan(final DDSpan span) {
    if (span.getDurationNano() == 0) {
      log.debug("{} - added to trace, but not complete.", span);
      return;
    }
    if (traceId != span.getTraceId()) {
      log.debug("{} - added to a mismatched trace.", span);
      return;
    }

    if (!isWritten.get()) {
      addFirst(span);
    } else {
      log.debug("{} - finished after trace reported.", span);
    }
    expireSpan(span);
  }

  /**
   * When using continuations, it's possible one may be used after all existing spans are otherwise
   * completed, so we need to wait till continuations are de-referenced before reporting.
   */
  public void registerContinuation(final ContinuableScope.Continuation continuation) {
    synchronized (continuation) {
      if (continuation.ref == null) {
        continuation.ref =
            new WeakReference<ContinuableScope.Continuation>(continuation, referenceQueue);
        weakReferences.add(continuation.ref);
        final int count = pendingReferenceCount.incrementAndGet();
        log.debug(
            "traceId: {} -- registered continuation {}. count = {}", traceId, continuation, count);
      } else {
        log.debug("continuation {} already registered in trace {}", continuation, traceId);
      }
    }
  }

  public void cancelContinuation(final ContinuableScope.Continuation continuation) {
    synchronized (continuation) {
      if (continuation.ref == null) {
        log.debug("continuation {} not registered in trace {}", continuation, traceId);
      } else {
        weakReferences.remove(continuation.ref);
        continuation.ref.clear();
        continuation.ref = null;
        expireReference();
      }
    }
  }

  private void expireReference() {
    final int count = pendingReferenceCount.decrementAndGet();
    if (count == 0) {
      write();
    }
    log.debug("traceId: {} -- Expired reference. count = {}", traceId, count);
  }

  private void write() {
    if (isWritten.compareAndSet(false, true)) {
      SPAN_CLEANER.pendingTraces.remove(this);
      if (!isEmpty()) {
        log.debug("Writing {} spans to {}.", this.size(), tracer.writer);
        tracer.write(this);
      }
    }
  }

  public synchronized boolean clean() {
    Reference ref;
    int count = 0;
    while ((ref = referenceQueue.poll()) != null) {
      weakReferences.remove(ref);
      count++;
      expireReference();
    }
    if (count > 0) {
      log.debug("{} unfinished spans garbage collected!", count);
    }
    return count > 0;
  }

  /**
   * This method ensures that garbage collection takes place, unlike <code>{@link System#gc()}
   * </code>. Useful for testing.
   */
  public static void awaitGC() {
    System.gc(); // For good measure.
    Object obj = new Object();
    final WeakReference ref = new WeakReference<>(obj);
    obj = null;
    while (ref.get() != null) {
      System.gc();
    }
  }

  static void close() {
    SPAN_CLEANER.close();
  }

  private static class SpanCleaner implements Runnable, Closeable {
    private static final long CLEAN_FREQUENCY = 1;
    private static final ThreadFactory FACTORY =
        new ThreadFactory() {
          @Override
          public Thread newThread(final Runnable r) {
            return new Thread(r, "dd-span-cleaner");
          }
        };

    private final ScheduledExecutorService executorService =
        Executors.newScheduledThreadPool(1, FACTORY);

    private final Set<PendingTrace> pendingTraces =
        Collections.newSetFromMap(new ConcurrentHashMap<PendingTrace, Boolean>());

    void start() {
      executorService.scheduleAtFixedRate(new SpanCleaner(), 0, CLEAN_FREQUENCY, TimeUnit.SECONDS);
      Runtime.getRuntime()
          .addShutdownHook(
              new Thread() {
                @Override
                public void run() {
                  PendingTrace.SpanCleaner.this.close();
                }
              });
    }

    @Override
    public void run() {
      for (final PendingTrace trace : pendingTraces) {
        trace.clean();
      }
    }

    @Override
    public void close() {
      executorService.shutdownNow();
      try {
        executorService.awaitTermination(500, TimeUnit.MILLISECONDS);
      } catch (final InterruptedException e) {
        log.info("Writer properly closed and async writer interrupted.");
      }
    }
  }
}
