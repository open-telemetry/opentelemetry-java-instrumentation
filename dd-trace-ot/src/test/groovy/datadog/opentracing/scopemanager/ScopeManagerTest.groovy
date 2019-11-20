package datadog.opentracing.scopemanager

import datadog.opentracing.DDSpan
import datadog.opentracing.DDSpanContext
import datadog.opentracing.DDTracer
import datadog.opentracing.NoopSpan
import datadog.trace.common.writer.ListWriter
import datadog.trace.context.ScopeListener
import datadog.trace.util.gc.GCUtils
import datadog.trace.util.test.DDSpecification
import spock.lang.Subject
import spock.lang.Timeout

import java.lang.ref.WeakReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

import static java.util.concurrent.TimeUnit.SECONDS

class ScopeManagerTest extends DDSpecification {
  def latch
  def writer
  def tracer

  @Subject
  def scopeManager

  def setup() {
    latch = new CountDownLatch(1)
    final currentLatch = latch
    writer = new ListWriter() {
      void incrementTraceCount() {
        currentLatch.countDown()
      }
    }
    tracer = new DDTracer(writer)
    scopeManager = tracer.scopeManager()
  }

  def cleanup() {
    scopeManager.tlsScope.remove()
  }

  def "non-ddspan activation results in a simple scope"() {
    when:
    def scope = scopeManager.activate(NoopSpan.INSTANCE, true)

    then:
    scopeManager.active() == scope
    scope instanceof SimpleScope

    when:
    scope.close()

    then:
    scopeManager.active() == null
  }

  def "threadlocal is empty"() {
    setup:
    def builder = tracer.buildSpan("test")
    builder.start()

    expect:
    scopeManager.active() == null
    writer.empty
  }

  def "threadlocal is active"() {
    when:
    def builder = tracer.buildSpan("test")
    def scope = builder.startActive(finishSpan)

    then:
    !spanFinished(scope.span())
    scopeManager.active() == scope
    scope instanceof ContinuableScope
    writer.empty

    when:
    scope.close()

    then:
    spanFinished(scope.span()) == finishSpan
    writer == [[scope.span()]] || !finishSpan
    scopeManager.active() == null

    where:
    finishSpan << [true, false]
  }

  def "sets parent as current upon close"() {
    setup:
    def parentScope = tracer.buildSpan("parent").startActive(finishSpan)
    def childScope = noopChild ? tracer.scopeManager().activate(NoopSpan.INSTANCE, finishSpan) : tracer.buildSpan("parent").startActive(finishSpan)

    expect:
    scopeManager.active() == childScope
    noopChild || childScope.span().context().parentId == parentScope.span().context().spanId
    noopChild || childScope.span().context().trace == parentScope.span().context().trace

    when:
    childScope.close()

    then:
    scopeManager.active() == parentScope
    noopChild || spanFinished(childScope.span()) == finishSpan
    !spanFinished(parentScope.span())
    writer == []

    when:
    parentScope.close()

    then:
    noopChild || spanFinished(childScope.span()) == finishSpan
    spanFinished(parentScope.span()) == finishSpan
    writer == [[parentScope.span(), childScope.span()]] || !finishSpan || noopChild
    scopeManager.active() == null

    where:
    finishSpan | noopChild
    true       | false
    false      | false
    true       | true
    false      | true
  }

  def "ContinuableScope only creates continuations when propagation is set"() {
    setup:
    def builder = tracer.buildSpan("test")
    def scope = (ContinuableScope) builder.startActive(true)
    def continuation = scope.capture()

    expect:
    continuation == null

    when:
    scope.setAsyncPropagation(true)
    continuation = scope.capture()
    then:
    continuation != null

    cleanup:
    continuation.close()
  }

  def "ContinuableScope doesn't close if non-zero"() {
    setup:
    def builder = tracer.buildSpan("test")
    def scope = (ContinuableScope) builder.startActive(true)
    scope.setAsyncPropagation(true)
    def continuation = scope.capture()

    expect:
    !spanFinished(scope.span())
    scopeManager.active() == scope
    scope instanceof ContinuableScope
    writer.empty

    when:
    scope.close()

    then:
    !spanFinished(scope.span())
    scopeManager.active() == null
    writer.empty

    when:
    continuation.activate()
    if (forceGC) {
      continuation = null // Continuation references also hold up traces.
      GCUtils.awaitGC() // The goal here is to make sure that continuation DOES NOT get GCed
      while (((DDSpanContext) scope.span().context()).trace.clean()) {
      }
    }
    if (autoClose) {
      if (continuation != null) {
        continuation.close()
      }
    }

    then:
    scopeManager.active() != null

    when:
    scopeManager.active().close()
    writer.waitForTraces(1)

    then:
    scopeManager.active() == null
    spanFinished(scope.span())
    writer == [[scope.span()]]

    where:
    autoClose | forceGC
    true      | true
    true      | false
    false     | true
  }

  def "Continuation.close closes parent scope"() {
    setup:
    def builder = tracer.buildSpan("test")
    def scope = (ContinuableScope) builder.startActive(true)
    scope.setAsyncPropagation(true)
    def continuation = scope.capture()

    when:
    /*
    Note: this API is inherently broken. Our scope implementation doesn't allow us to close scopes
    in random order, yet when we close continuation we attempt to close scope by default.
    And in fact continuation trying to close parent scope is most likely a bug.
     */
    continuation.close(true)

    then:
    scopeManager.active() == null
    !spanFinished(scope.span())

    when:
    scope.span().finish()

    then:
    scopeManager.active() == null
  }

  def "Continuation.close doesn't close parent scope"() {
    setup:
    def builder = tracer.buildSpan("test")
    def scope = (ContinuableScope) builder.startActive(true)
    scope.setAsyncPropagation(true)
    def continuation = scope.capture()

    when:
    continuation.close(false)

    then:
    scopeManager.active() == scope
  }

  def "Continuation.close doesn't close parent scope, span finishes"() {
    /*
    This is highly confusing behaviour. Sequence of events is as following:
      * Scope gets created along with span and with finishOnClose == true.
      * Continuation gets created for that scope.
      * Scope is closed.
        At this point scope is not really closed. It is removed from scope
        stack, but it is still alive because there is a live continuation attached
        to it. This also means span is not closed.
      * Continuation is closed.
        This triggers final closing of scope and closing of the span.

     This is confusing because expected behaviour is for span to be closed
     with the scope when finishOnClose = true, but in fact span lingers until
     continuation is closed.
     */
    setup:
    def builder = tracer.buildSpan("test")
    def scope = (ContinuableScope) builder.startActive(true)
    scope.setAsyncPropagation(true)
    def continuation = scope.capture()
    scope.close()

    when:
    continuation.close(false)

    then:
    scopeManager.active() == null
    spanFinished(scope.span())
    writer == [[scope.span()]]
  }

  @Timeout(value = 60, unit = SECONDS)
  def "hard reference on continuation prevents trace from reporting"() {
    setup:
    def builder = tracer.buildSpan("test")
    def scope = (ContinuableScope) builder.startActive(false)
    def span = scope.span()
    scope.setAsyncPropagation(true)
    def continuation = scope.capture()
    scope.close()
    span.finish()

    expect:
    scopeManager.active() == null
    spanFinished(span)
    writer == []

    when:
    if (forceGC) {
      def continuationRef = new WeakReference<>(continuation)
      continuation = null // Continuation references also hold up traces.
      GCUtils.awaitGC(continuationRef)
      latch.await(60, SECONDS)
    }
    if (autoClose) {
      if (continuation != null) {
        continuation.close()
      }
    }

    then:
    forceGC ? true : writer == [[span]]

    where:
    autoClose | forceGC
    true      | true
    true      | false
    false     | true
  }

  def "continuation restores trace"() {
    setup:
    def parentScope = tracer.buildSpan("parent").startActive(true)
    def parentSpan = parentScope.span()
    ContinuableScope childScope = (ContinuableScope) tracer.buildSpan("parent").startActive(true)
    childScope.setAsyncPropagation(true)
    def childSpan = childScope.span()

    def continuation = childScope.capture()
    childScope.close()

    expect:
    parentSpan.context().trace == childSpan.context().trace
    scopeManager.active() == parentScope
    !spanFinished(childSpan)
    !spanFinished(parentSpan)

    when:
    parentScope.close()
    // parent span is finished, but trace is not reported

    then:
    scopeManager.active() == null
    !spanFinished(childSpan)
    spanFinished(parentSpan)
    writer == []

    when:
    def newScope = continuation.activate()
    newScope.setAsyncPropagation(true)
    def newContinuation = newScope.capture()

    then:
    newScope instanceof ContinuableScope
    scopeManager.active() == newScope
    newScope != childScope && newScope != parentScope
    newScope.span() == childSpan
    !spanFinished(childSpan)
    spanFinished(parentSpan)
    writer == []

    when:
    newScope.close()
    newContinuation.activate().close()

    then:
    scopeManager.active() == null
    spanFinished(childSpan)
    spanFinished(parentSpan)
    writer == [[childSpan, parentSpan]]
  }

  def "continuation allows adding spans even after other spans were completed"() {
    setup:
    def builder = tracer.buildSpan("test")
    def scope = (ContinuableScope) builder.startActive(false)
    def span = scope.span()
    scope.setAsyncPropagation(true)
    def continuation = scope.capture()
    scope.close()
    span.finish()

    def newScope = continuation.activate()

    expect:
    newScope instanceof ContinuableScope
    newScope != scope
    scopeManager.active() == newScope
    spanFinished(span)
    writer == []

    when:
    def childScope = tracer.buildSpan("child").startActive(true)
    def childSpan = childScope.span()
    childScope.close()
    scopeManager.active().close()

    then:
    scopeManager.active() == null
    spanFinished(childSpan)
    childSpan.context().parentId == span.context().spanId
    writer == [[childSpan, span]]
  }

  def "add scope listener"() {
    setup:
    AtomicInteger activatedCount = new AtomicInteger(0)
    AtomicInteger closedCount = new AtomicInteger(0)

    scopeManager.addScopeListener(new ScopeListener() {
      @Override
      void afterScopeActivated() {
        activatedCount.incrementAndGet()
      }

      @Override
      void afterScopeClosed() {
        closedCount.incrementAndGet()
      }
    })

    when:
    DDScope scope1 = scopeManager.activate(NoopSpan.INSTANCE, true)

    then:
    activatedCount.get() == 1
    closedCount.get() == 0

    when:
    DDScope scope2 = scopeManager.activate(NoopSpan.INSTANCE, true)

    then:
    activatedCount.get() == 2
    closedCount.get() == 0

    when:
    scope2.close()

    then:
    activatedCount.get() == 3
    closedCount.get() == 1

    when:
    scope1.close()

    then:
    activatedCount.get() == 3
    closedCount.get() == 2

    when:
    DDScope continuableScope = tracer.buildSpan("foo").startActive(true)

    then:
    continuableScope instanceof ContinuableScope
    activatedCount.get() == 4

    when:
    DDScope childContinuableScope = tracer.buildSpan("child").startActive(true)

    then:
    childContinuableScope instanceof ContinuableScope
    activatedCount.get() == 5
    closedCount.get() == 2

    when:
    childContinuableScope.close()

    then:
    activatedCount.get() == 6
    closedCount.get() == 3

    when:
    continuableScope.close()

    then:
    activatedCount.get() == 6
    closedCount.get() == 4
  }

  boolean spanFinished(DDSpan span) {
    return span?.isFinished()
  }
}
