package datadog.opentracing.scopemanager

import datadog.opentracing.DDSpan
import datadog.opentracing.DDTracer
import datadog.trace.common.writer.ListWriter
import io.opentracing.Scope
import io.opentracing.Span
import spock.lang.Specification
import spock.lang.Subject

import java.util.concurrent.atomic.AtomicReference

class ScopeManagerTest extends Specification {
  def writer = new ListWriter()
  def tracer = new DDTracer(writer)

  @Subject
  def scopeManager = tracer.scopeManager()

  def cleanup() {
    scopeManager.tlsScope.remove()
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
    builder.withTag("dd.use.ref.counting", false)
    def scope = builder.startActive(true)

    then:
    !spanReported(scope.span())
    scopeManager.active() == scope
    scope instanceof ContextualScopeManager.ThreadLocalScope
    writer.empty

    when:
    scope.close()

    then:
    spanReported(scope.span())
    writer == [[scope.span()]]
    scopeManager.active() == null
  }

  def "threadlocal is active with ref counting scope"() {
    setup:
    def builder = tracer.buildSpan("test")
    builder.withReferenceCounting()
    def scope = builder.startActive(true)

    expect:
    !spanReported(scope.span())
    scopeManager.active() == scope
    scope instanceof ContextualScopeManager.RefCountingScope

    when:
    scope.close()

    then:
    spanReported(scope.span())
    writer == [[scope.span()]]
    scopeManager.active() == null
  }

  def "threadlocal is active with ref counting scope using tag"() {
    setup:
    def builder = tracer.buildSpan("test")
    builder.withTag("dd.use.ref.counting", true)
    def scope = builder.startActive(true)

    expect:
    !spanReported(scope.span())
    scopeManager.active() == scope
    scope instanceof ContextualScopeManager.RefCountingScope

    when:
    scope.close()

    then:
    spanReported(scope.span())
    writer == [[scope.span()]]
    scopeManager.active() == null
  }

  def "ref counting scope doesn't close if non-zero"() {
    setup:
    def builder = tracer.buildSpan("test")
    builder.withReferenceCounting()
    def scope = (ContextualScopeManager.RefCountingScope) builder.startActive(true)
    def continuation = scope.capture()

    expect:
    !spanReported(scope.span())
    scopeManager.active() == scope
    scope instanceof ContextualScopeManager.RefCountingScope
    writer.empty


    when:
    scope.close()

    then:
    !spanReported(scope.span())
    scopeManager.active() == null
    writer.empty

    when:
    continuation.activate()

    then:
    scopeManager.active() != null

    when:
    scopeManager.active().close()

    then:
    scopeManager.active() == null
    spanReported(scope.span())
    writer == [[scope.span()]]
  }

  def "context takes control"() {
    setup:
    contexts.each {
      scopeManager.addScopeContext(it)
    }
    def builder = tracer.buildSpan("test")
    def scope = (AtomicReferenceScope) builder.startActive(true)

    expect:
    scopeManager.tlsScope.get() == null
    scopeManager.active() == scope
    contexts[activeIndex].get() == scope.get()
    writer.empty

    where:
    activeIndex | contexts
    0           | [new AtomicReferenceScope(true)]
    0           | [new AtomicReferenceScope(true), new AtomicReferenceScope(true)]
    1           | [new AtomicReferenceScope(false), new AtomicReferenceScope(true), new AtomicReferenceScope(true), new AtomicReferenceScope(false)]
    2           | [new AtomicReferenceScope(false), new AtomicReferenceScope(false), new AtomicReferenceScope(true)]
  }

  def "disabled context is ignored"() {
    setup:
    contexts.each {
      scopeManager.addScopeContext(it)
    }
    def builder = tracer.buildSpan("test")
    def scope = builder.startActive(true)

    expect:
    scopeManager.tlsScope.get() == scope
    scopeManager.active() == scope
    writer.empty
    contexts.each {
      assert it.get() == null
    } == contexts

    where:
    contexts                                                                                            | _
    []                                                                                                  | _
    [new AtomicReferenceScope(false)]                                                                   | _
    [new AtomicReferenceScope(false), new AtomicReferenceScope(false)]                                  | _
    [new AtomicReferenceScope(false), new AtomicReferenceScope(false), new AtomicReferenceScope(false)] | _
  }

  boolean spanReported(DDSpan span) {
    return span.durationNano != 0
  }

  class AtomicReferenceScope extends AtomicReference<Span> implements ScopeContext, Scope {
    final boolean enabled

    AtomicReferenceScope(boolean enabled) {
      this.enabled = enabled
    }

    @Override
    boolean inContext() {
      return enabled
    }

    @Override
    void close() {
      getAndSet(null).finish()
    }

    @Override
    Span span() {
      return get()
    }

    @Override
    Scope activate(Span span, boolean finishSpanOnClose) {
      set(span)
      return this
    }

    @Override
    Scope active() {
      return get() == null ? null : this
    }
  }
}
