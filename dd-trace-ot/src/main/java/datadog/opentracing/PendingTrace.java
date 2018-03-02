package datadog.opentracing;

import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import datadog.opentracing.scopemanager.ContinuableScope;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Set;
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
  static {
    SpanCleaner.start();
  }

  private final DDTracer tracer;
  private final long traceId;

  private final ReferenceQueue referenceQueue = new ReferenceQueue();
  private final Set<WeakReference<?>> weakReferences = Sets.newConcurrentHashSet();

  private final AtomicInteger pendingReferenceCount = new AtomicInteger(0);

  /** Ensure a trace is never written multiple times */
  private final AtomicBoolean isWritten = new AtomicBoolean(false);

  PendingTrace(final DDTracer tracer, final long traceId) {
    this.tracer = tracer;
    this.traceId = traceId;
    SpanCleaner.pendingTraces.add(this);
  }

  public void registerSpan(final DDSpan span) {
    if (span.context().getTraceId() != traceId) {
      log.warn("{} - span registered for wrong trace ({})", span, traceId);
      return;
    }
    span.ref = new WeakReference<DDSpan>(span, referenceQueue);
    weakReferences.add(span.ref);
    pendingReferenceCount.incrementAndGet();
  }

  private void expireSpan(final DDSpan span) {
    if (span.context().getTraceId() != traceId) {
      log.warn("{} - span expired for wrong trace ({})", span, traceId);
      return;
    }
    weakReferences.remove(span.ref);
    span.ref.clear();
    span.ref = null;
    expireReference();
  }

  public void addSpan(final DDSpan span) {
    if (span.getDurationNano() == 0) {
      log.warn("{} - added to trace, but not complete.", span);
      return;
    }
    if (traceId != span.getTraceId()) {
      log.warn("{} - added to a mismatched trace.", span);
      return;
    }

    if (!isWritten.get()) {
      addFirst(span);
      expireSpan(span);
    } else {
      log.warn("{} - finished after trace reported.", span);
    }
  }

  /**
   * When using continuations, it's possible one may be used after all existing spans are otherwise
   * completed, so we need to wait till continuations are de-referenced before reporting.
   */
  public void registerContinuation(final ContinuableScope.Continuation continuation) {
    weakReferences.add(
        new WeakReference<ContinuableScope.Continuation>(continuation, referenceQueue));
    pendingReferenceCount.incrementAndGet();
  }

  private void expireReference() {
    if (pendingReferenceCount.decrementAndGet() == 0) {
      write();
    }
  }

  private void write() {
    if (isWritten.compareAndSet(false, true)) {
      SpanCleaner.pendingTraces.remove(this);
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

  private static class SpanCleaner implements Runnable {
    private static final long CLEAN_FREQUENCY = 1;
    private static final ThreadFactory FACTORY =
        new ThreadFactoryBuilder().setNameFormat("dd-span-cleaner-%d").setDaemon(true).build();

    private static final ScheduledExecutorService EXECUTOR_SERVICE =
        Executors.newScheduledThreadPool(1, FACTORY);

    static final Set<PendingTrace> pendingTraces = Sets.newConcurrentHashSet();

    static void start() {
      EXECUTOR_SERVICE.scheduleAtFixedRate(new SpanCleaner(), 0, CLEAN_FREQUENCY, TimeUnit.SECONDS);
    }

    @Override
    public void run() {
      for (final PendingTrace trace : pendingTraces) {
        trace.clean();
      }
    }
  }
}
