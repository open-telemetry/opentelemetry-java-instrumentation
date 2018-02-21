package datadog.opentracing.scopemanager

import datadog.opentracing.DDTracer
import datadog.trace.common.writer.ListWriter
import io.opentracing.Scope
import io.opentracing.Span
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Timeout
import spock.lang.Unroll

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@Timeout(1)
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
    def scope = builder.startActive(finishSpan)

    then:
    !spanReported(scope.span())
    scopeManager.active() == scope
    scope instanceof RefCountingScope
    writer.empty

    when:
    scope.close()

    then:
    spanReported(scope.span()) == finishSpan
    writer == [[scope.span()]] || !finishSpan
    scopeManager.active() == null

    where:
    finishSpan << [true, false]
  }

  def "sets parent as current upon close"() {
    setup:
    def parentScope = tracer.buildSpan("parent").startActive(finishSpan)
    def childScope = tracer.buildSpan("parent").startActive(finishSpan)

    expect:
    scopeManager.active() == childScope
    childScope.span().context().parentId == parentScope.span().context().spanId
    childScope.span().context().trace == parentScope.span().context().trace

    when:
    childScope.close()

    then:
    scopeManager.active() == parentScope
    spanReported(childScope.span()) == finishSpan
    !spanReported(parentScope.span())
    writer == []

    when:
    parentScope.close()

    then:
    spanReported(childScope.span()) == finishSpan
    spanReported(parentScope.span()) == finishSpan
    writer == [[parentScope.span(), childScope.span()]] || !finishSpan
    scopeManager.active() == null

    where:
    finishSpan << [true, false]
  }

  def "ref counting scope doesn't close if non-zero"() {
    setup:
    def builder = tracer.buildSpan("test")
    def scope = (RefCountingScope) builder.startActive(true)
    def continuation = scope.capture()

    expect:
    !spanReported(scope.span())
    scopeManager.active() == scope
    scope instanceof RefCountingScope
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

  def "continuation restores trace"() {
    setup:
    def parentScope = tracer.buildSpan("parent").startActive(false) //false or trace is reported early
    def parentSpan = parentScope.span()
    RefCountingScope childScope = (RefCountingScope) tracer.buildSpan("parent").startActive(true)
    def childSpan = childScope.span()

    def cont = childScope.capture()
    childScope.close()

    expect:
    parentSpan.context().trace == childSpan.context().trace
    scopeManager.active() == parentScope
    !spanReported(childSpan)
    !spanReported(parentSpan)

    when:
    parentScope.close()
    // If finishSpanOnClose was true for the parent, the trace would get reported even though the child was not done.

    then:
    scopeManager.active() == null
    !spanReported(childSpan)
    !spanReported(parentSpan)
    writer == []

    when:
    def newScope = cont.activate()

    then:
    scopeManager.active() == newScope
    newScope != childScope && newScope != parentScope
    newScope.span() == childSpan
    !spanReported(childSpan)
    !spanReported(parentSpan)
    writer == []

    when:
    newScope.close()

    then:
    scopeManager.active() == null
    spanReported(childSpan)
    !spanReported(parentSpan)
    writer == []

    when:
    // Since finishSpanOnClose was false, we must manually finish the span.
    parentSpan.finish()

    then:
    spanReported(childSpan)
    spanReported(parentSpan)
    writer == [[parentSpan, childSpan]]
  }

  @Unroll
  def "context takes control (#active)"() {
    setup:
    contexts.each {
      scopeManager.addScopeContext(it)
    }
    def builder = tracer.buildSpan("test")
    def scope = (AtomicReferenceScope) builder.startActive(true)

    expect:
    scopeManager.tlsScope.get() == null
    scopeManager.active() == scope
    contexts[active].get() == scope.get()
    writer.empty

    where:
    active | contexts
    0      | [new AtomicReferenceScope(true)]
    1      | [new AtomicReferenceScope(true), new AtomicReferenceScope(true)]
    3      | [new AtomicReferenceScope(false), new AtomicReferenceScope(true), new AtomicReferenceScope(false), new AtomicReferenceScope(true)]
  }

  @Unroll
  def "disabled context is ignored (#contexts.size)"() {
    setup:
    contexts.each {
      scopeManager.addScopeContext(it)
    }
    def builder = tracer.buildSpan("test")
    def scope = builder.startActive(true)

    expect:
    contexts.findAll {
      it.get() != null
    } == []

    scopeManager.tlsScope.get() == scope
    scopeManager.active() == scope
    writer.empty

    where:
    contexts                                                                                            | _
    []                                                                                                  | _
    [new AtomicReferenceScope(false)]                                                                   | _
    [new AtomicReferenceScope(false), new AtomicReferenceScope(false)]                                  | _
    [new AtomicReferenceScope(false), new AtomicReferenceScope(false), new AtomicReferenceScope(false)] | _
  }

  @Unroll
  def "threadlocal to context with capture (#active)"() {
    setup:
    contexts.each {
      scopeManager.addScopeContext(it)
    }
    RefCountingScope scope = (RefCountingScope) tracer.buildSpan("parent").startActive(true)

    expect:
    scopeManager.tlsScope.get() == scope

    when:
    def cont = scope.capture()
    scope.close()

    then:
    scopeManager.tlsScope.get() == null

    when:
    active.each {
      ((AtomicBoolean) contexts[it].enabled).set(true)
    }
    cont.activate()

    then:
    scopeManager.tlsScope.get() == null

    where:
    active | contexts
    [0]    | [new AtomicReferenceScope(false)]
    [0]    | [new AtomicReferenceScope(false), new AtomicReferenceScope(false)]
    [1]    | [new AtomicReferenceScope(false), new AtomicReferenceScope(false), new AtomicReferenceScope(false)]
    [2]    | [new AtomicReferenceScope(false), new AtomicReferenceScope(false), new AtomicReferenceScope(false)]
    [0, 2] | [new AtomicReferenceScope(false), new AtomicReferenceScope(false), new AtomicReferenceScope(false)]
  }

  @Unroll
  def "context to threadlocal (#contexts.size)"() {
    setup:
    contexts.each {
      scopeManager.addScopeContext(it)
    }
    def scope = tracer.buildSpan("parent").startActive(false)
    def span = scope.span()

    expect:
    scope instanceof AtomicReferenceScope
    scopeManager.tlsScope.get() == null

    when:
    scope.close()
    contexts.each {
      ((AtomicBoolean) it.enabled).set(false)
    }
    scope = scopeManager.activate(span, true)

    then:
    scope instanceof RefCountingScope
    scopeManager.tlsScope.get() == scope

    where:
    contexts                                                         | _
    [new AtomicReferenceScope(true)]                                 | _
    [new AtomicReferenceScope(true), new AtomicReferenceScope(true)] | _
  }

  boolean spanReported(Span span) {
    return span.durationNano != 0
  }

  class AtomicReferenceScope extends AtomicReference<Span> implements ScopeContext, Scope {
    final AtomicBoolean enabled

    AtomicReferenceScope(boolean enabled) {
      this.enabled = new AtomicBoolean(enabled)
    }

    @Override
    boolean inContext() {
      return enabled.get()
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

    String toString() {
      return "Ref: " + super.toString()
    }
  }
}
