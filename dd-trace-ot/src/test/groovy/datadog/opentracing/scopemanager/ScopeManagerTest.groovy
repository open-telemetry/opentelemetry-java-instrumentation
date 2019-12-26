package datadog.opentracing.scopemanager

import datadog.opentracing.DDSpan
import datadog.opentracing.DDTracer
import datadog.opentracing.NoopSpan
import datadog.trace.common.writer.ListWriter
import datadog.trace.context.ScopeListener
import datadog.trace.util.test.DDSpecification
import spock.lang.Subject

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

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
