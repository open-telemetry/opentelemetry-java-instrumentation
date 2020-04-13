package datadog.opentracing;

import datadog.common.exec.CommonTaskExecutor;
import datadog.common.exec.CommonTaskExecutor.Task;
import datadog.opentracing.scopemanager.ContinuableScope;
import datadog.trace.common.util.Clock;
import java.io.Closeable;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PendingTrace extends ConcurrentLinkedDeque<DDSpan> {
  private static final AtomicReference<SpanCleaner> SPAN_CLEANER = new AtomicReference<>();

  private final DDTracer tracer;
  private final BigInteger traceId;

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

  // We must maintain a separate count because ConcurrentLinkedDeque.size() is a linear operation.
  private final AtomicInteger completedSpanCount = new AtomicInteger(0);
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

  PendingTrace(final DDTracer tracer, final BigInteger traceId) {
    this.tracer = tracer;
    this.traceId = traceId;

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
                completedSpanCount.decrementAndGet();
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
      // TODO attempt to flatten and report if top level spans are finished. (for accurate metrics)
      log.debug(
          "trace {} : {} unfinished spans garbage collected. Trace will not report.",
          traceId,
          count);
    }
    return count > 0;
  }

  @Override
  public void addFirst(final DDSpan span) {
    super.addFirst(span);
    completedSpanCount.incrementAndGet();
  }

  @Override
  public int size() {
    return completedSpanCount.get();
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

  // FIXME: it should be possible to simplify this logic and avod having SpanCleaner and
  // SpanCleanerTask
  private static class SpanCleaner implements Runnable, Closeable {
    private static final long CLEAN_FREQUENCY = 1;

    private final Set<PendingTrace> pendingTraces =
        Collections.newSetFromMap(new ConcurrentHashMap<PendingTrace, Boolean>());

    public SpanCleaner() {
      CommonTaskExecutor.INSTANCE.scheduleAtFixedRate(
          SpanCleanerTask.INSTANCE,
          this,
          0,
          CLEAN_FREQUENCY,
          TimeUnit.SECONDS,
          "Pending trace cleaner");
    }

    @Override
    public void run() {
      for (final PendingTrace trace : pendingTraces) {
        trace.clean();
      }
    }

    @Override
    public void close() {
      // Make sure that whatever was left over gets cleaned up
      run();
    }
  }

  /*
   * Important to use explicit class to avoid implicit hard references to cleaners from within executor.
   */
  private static class SpanCleanerTask implements Task<SpanCleaner> {

    static final SpanCleanerTask INSTANCE = new SpanCleanerTask();

    @Override
    public void run(final SpanCleaner target) {
      target.run();
    }
  }
}
