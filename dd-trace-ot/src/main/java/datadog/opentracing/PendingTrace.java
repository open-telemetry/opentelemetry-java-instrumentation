package datadog.opentracing;

import datadog.opentracing.scopemanager.ContinuableScope;
import datadog.trace.common.util.Clock;
import java.io.Closeable;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PendingTrace extends ConcurrentLinkedDeque<DDSpan> {
  private static final AtomicReference<SpanCleaner> SPAN_CLEANER = new AtomicReference<>();

  private final DDTracer tracer;
  private final String traceId;
  private final Map<String, String> serviceNameMappings;

  // TODO: consider moving these time fields into DDTracer to ensure that traces have precise
  // relative time
  /** Trace start time in nano seconds measured up to a millisecond accuracy */
  private final long startTimeNano;
  /** Nano second ticks value at trace start */
  private final long startNanoTicks;

  private final ReferenceQueue referenceQueue = new ReferenceQueue();
  private final Set<WeakReference<?>> weakReferences =
      Collections.newSetFromMap(new ConcurrentHashMap<WeakReference<?>, Boolean>());

  private final AtomicInteger pendingReferenceCount = new AtomicInteger(0);
  /**
   * During a trace there are cases where the root span must be accessed (e.g. priority sampling and
   * trace-search tags).
   *
   * <p>Use a weak ref because we still need to handle buggy cases where the root span is not
   * correctly closed (see SpanCleaner).
   *
   * <p>The root span will be available in non-buggy cases because it has either finished and
   * strongly ref'd in this queue or is unfinished and ref'd in a ContinuableScope.
   */
  private final AtomicReference<WeakReference<DDSpan>> rootSpan = new AtomicReference<>();

  /** Ensure a trace is never written multiple times */
  private final AtomicBoolean isWritten = new AtomicBoolean(false);

  PendingTrace(
      final DDTracer tracer, final String traceId, final Map<String, String> serviceNameMappings) {
    this.tracer = tracer;
    this.traceId = traceId;
    this.serviceNameMappings = serviceNameMappings;

    startTimeNano = Clock.currentNanoTime();
    startNanoTicks = Clock.currentNanoTicks();

    addPendingTrace();
  }

  /**
   * Current timestamp in nanoseconds.
   *
   * <p>Note: it is not possible to get 'real' nanosecond time. This method uses trace start time
   * (which has millisecond precision) as a reference and it gets time with nanosecond precision
   * after that. This means time measured within same Trace in different Spans is relatively correct
   * with nanosecond precision.
   *
   * @return timestamp in nanoseconds
   */
  public long getCurrentTimeNano() {
    return startTimeNano + Math.max(0, Clock.currentNanoTicks() - startNanoTicks);
  }

  public void registerSpan(final DDSpan span) {
    if (traceId == null || span.context() == null) {
      log.error(
          "Failed to register span ({}) due to null PendingTrace traceId or null span context",
          span);
      return;
    }
    if (!traceId.equals(span.context().getTraceId())) {
      log.debug("{} - span registered for wrong trace ({})", span, traceId);
      return;
    }
    rootSpan.compareAndSet(null, new WeakReference<>(span));
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
    if (traceId == null || span.context() == null) {
      log.error(
          "Failed to expire span ({}) due to null PendingTrace traceId or null span context", span);
      return;
    }
    if (!traceId.equals(span.context().getTraceId())) {
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
    if (traceId == null || span.context() == null) {
      log.error(
          "Failed to add span ({}) due to null PendingTrace traceId or null span context", span);
      return;
    }
    if (!traceId.equals(span.getTraceId())) {
      log.debug("{} - added to a mismatched trace.", span);
      return;
    }

    if (!isWritten.get()) {
      if (serviceNameMappings.containsKey(span.getServiceName())) {
        span.setServiceName(serviceNameMappings.get(span.getServiceName()));
      }

      addFirst(span);
    } else {
      log.debug("{} - finished after trace reported.", span);
    }
    expireSpan(span);
  }

  public DDSpan getRootSpan() {
    final WeakReference<DDSpan> rootRef = rootSpan.get();
    return rootRef == null ? null : rootRef.get();
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
    } else {
      if (tracer.getPartialFlushMinSpans() > 0 && size() > tracer.getPartialFlushMinSpans()) {
        synchronized (this) {
          if (size() > tracer.getPartialFlushMinSpans()) {
            final DDSpan rootSpan = getRootSpan();
            final List<DDSpan> partialTrace = new ArrayList(size());
            final Iterator<DDSpan> it = iterator();
            while (it.hasNext()) {
              final DDSpan span = it.next();
              if (span != rootSpan) {
                partialTrace.add(span);
                it.remove();
              }
            }
            log.debug("Writing partial trace {} of size {}", traceId, partialTrace.size());
            tracer.write(partialTrace);
          }
        }
      }
    }
    log.debug("traceId: {} -- Expired reference. count = {}", traceId, count);
  }

  private synchronized void write() {
    if (isWritten.compareAndSet(false, true)) {
      removePendingTrace();
      if (!isEmpty()) {
        log.debug("Writing {} spans to {}.", size(), tracer.writer);
        tracer.write(this);
      }
    }
  }

  public synchronized boolean clean() {
    Reference ref;
    int count = 0;
    while ((ref = referenceQueue.poll()) != null) {
      weakReferences.remove(ref);
      if (isWritten.compareAndSet(false, true)) {
        removePendingTrace();
        // preserve throughput count.
        // Don't report the trace because the data comes from buggy uses of the api and is suspect.
        tracer.incrementTraceCount();
      }
      count++;
      expireReference();
    }
    if (count > 0) {
      log.debug(
          "trace {} : {} unfinished spans garbage collected. Trace will not report.",
          traceId,
          count);
    }
    return count > 0;
  }

  private void addPendingTrace() {
    final SpanCleaner cleaner = SPAN_CLEANER.get();
    if (cleaner != null) {
      cleaner.pendingTraces.add(this);
    }
  }

  private void removePendingTrace() {
    final SpanCleaner cleaner = SPAN_CLEANER.get();
    if (cleaner != null) {
      cleaner.pendingTraces.remove(this);
    }
  }

  static void initialize() {
    final SpanCleaner oldCleaner = SPAN_CLEANER.getAndSet(new SpanCleaner());
    if (oldCleaner != null) {
      oldCleaner.close();
    }
  }

  static void close() {
    final SpanCleaner cleaner = SPAN_CLEANER.getAndSet(null);
    if (cleaner != null) {
      cleaner.close();
    }
  }

  private static class SpanCleaner implements Runnable, Closeable {
    private static final long CLEAN_FREQUENCY = 1;
    private static final ThreadFactory FACTORY =
        new ThreadFactory() {
          @Override
          public Thread newThread(final Runnable r) {
            final Thread thread = new Thread(r, "dd-span-cleaner");
            thread.setDaemon(true);
            return thread;
          }
        };

    private final ScheduledExecutorService executorService =
        Executors.newScheduledThreadPool(1, FACTORY);

    private final Set<PendingTrace> pendingTraces =
        Collections.newSetFromMap(new ConcurrentHashMap<PendingTrace, Boolean>());

    public SpanCleaner() {
      executorService.scheduleAtFixedRate(this, 0, CLEAN_FREQUENCY, TimeUnit.SECONDS);
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

      // Make sure that whatever was left over gets cleaned up
      run();
    }
  }
}
