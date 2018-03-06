package datadog.opentracing.scopemanager

import datadog.opentracing.DDSpan
import datadog.opentracing.DDSpanContext
import datadog.opentracing.DDTracer
import datadog.opentracing.PendingTrace
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
    def childScope = tracer.buildSpan("parent").startActive(finishSpan)

    expect:
    scopeManager.active() == childScope
    childScope.span().context().parentId == parentScope.span().context().spanId
    childScope.span().context().trace == parentScope.span().context().trace

    when:
    childScope.close()

    then:
    scopeManager.active() == parentScope
    spanFinished(childScope.span()) == finishSpan
    !spanFinished(parentScope.span())
    writer == []

    when:
    parentScope.close()

    then:
    spanFinished(childScope.span()) == finishSpan
    spanFinished(parentScope.span()) == finishSpan
    writer == [[parentScope.span(), childScope.span()]] || !finishSpan
    scopeManager.active() == null

    where:
    finishSpan << [true, false]
  }

  def "ref counting scope doesn't close if non-zero"() {
    setup:
    def builder = tracer.buildSpan("test")
    def scope = (ContinuableScope) builder.startActive(true)
    def continuation = scope.capture(true)

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
      PendingTrace.awaitGC()
      ((DDSpanContext) scope.span().context()).trace.clean()
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

  def "hard reference on continuation prevents trace from reporting"() {
    setup:
    def builder = tracer.buildSpan("test")
    def scope = (ContinuableScope) builder.startActive(false)
    def span = scope.span()
    def continuation = scope.capture(true)
    scope.close()
    span.finish()

    expect:
    scopeManager.active() == null
    spanFinished(span)
    writer == []

    when:
    if (forceGC) {
      continuation = null // Continuation references also hold up traces.
      PendingTrace.awaitGC()
      ((DDSpanContext) span.context()).trace.clean()
      writer.waitForTraces(1)
    }
    if (autoClose) {
      if (continuation != null) {
        continuation.close()
      }
    }

    then:
    writer == [[span]]

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
    def childSpan = childScope.span()

    def continuation = childScope.capture(true)
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

    then:
    scopeManager.active() == newScope.wrapped
    newScope != childScope && newScope != parentScope
    newScope.span() == childSpan
    !spanFinished(childSpan)
    spanFinished(parentSpan)
    writer == []

    when:
    newScope.close()

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
    def continuation = scope.capture(false)
    scope.close()
    span.finish()

    def newScope = continuation.activate()

    expect:
    newScope != scope
    scopeManager.active() == newScope.wrapped
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
    writer == []

    when:
    if (closeScope) {
      newScope.close()
    }
    if (closeContinuation) {
      continuation.close()
    }

    then:
    writer == [[childSpan, span]]

    where:
    closeScope | closeContinuation
    true       | false
    false      | true
    true       | true
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
    ContinuableScope scope = (ContinuableScope) tracer.buildSpan("parent").startActive(true)

    expect:
    scopeManager.tlsScope.get() == scope

    when:
    def cont = scope.capture(true)
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
    scope instanceof ContinuableScope
    scopeManager.tlsScope.get() == scope

    where:
    contexts                                                         | _
    [new AtomicReferenceScope(true)]                                 | _
    [new AtomicReferenceScope(true), new AtomicReferenceScope(true)] | _
  }

  boolean spanFinished(Span span) {
    return ((DDSpan) span).isFinished()
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
