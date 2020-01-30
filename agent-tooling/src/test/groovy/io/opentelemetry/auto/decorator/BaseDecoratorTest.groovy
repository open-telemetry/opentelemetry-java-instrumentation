package io.opentelemetry.auto.decorator

import io.opentelemetry.auto.api.MoreTags
import io.opentelemetry.auto.instrumentation.api.Tags
import io.opentelemetry.auto.test.MapGetter
import io.opentelemetry.auto.test.MapSetter
import io.opentelemetry.auto.util.test.AgentSpecification
import io.opentelemetry.trace.Span
import io.opentelemetry.trace.SpanContext
import io.opentelemetry.trace.Status
import spock.lang.Shared

class BaseDecoratorTest extends AgentSpecification {

  @Shared
  def decorator = newDecorator()

  def span = Mock(Span)

  def "test getCurrentSpan"() {
    when:
    decorator.beginSpan("some-span")
    def aSpan = decorator.getCurrentSpan()
    def attr = aSpan.getAttributes()

    then:
    aSpan != null
    aSpan.getContext().getSpanId() != null
    attr[MoreTags.SPAN_TYPE] == decorator.getSpanType()
    attr[Tags.COMPONENT] == "test-component"
  }

  def "test endSpan"() {
    when:
    decorator.endSpan(span)

    then:
    1 * span.end()
  }

  def "test endSpanAndScope"() {
    when:
    def spanAndScope = decorator.createSpanScopePair(span)
    decorator.endSpanAndScope(spanAndScope)

    then:
    spanAndScope != null
    spanAndScope.getScope() != null
    1 * span.end()
  }

  def "test afterStart"() {
    when:
    decorator.afterStart(span)

    then:
    1 * span.setAttribute(MoreTags.SPAN_TYPE, decorator.getSpanType())
    1 * span.setAttribute(Tags.COMPONENT, "test-component")
    _ * span.setAttribute(_, _) // Want to allow other calls from child implementations.
    0 * _
  }

  def "test onPeerConnection"() {
    when:
    decorator.onPeerConnection(span, connection)

    then:
    if (connection.getAddress()) {
      2 * span.setAttribute(Tags.PEER_HOSTNAME, connection.hostName)
    } else {
      1 * span.setAttribute(Tags.PEER_HOSTNAME, connection.hostName)
    }
    1 * span.setAttribute(Tags.PEER_PORT, connection.port)
    if (connection.address instanceof Inet4Address) {
      1 * span.setAttribute(Tags.PEER_HOST_IPV4, connection.address.hostAddress)
    }
    if (connection.address instanceof Inet6Address) {
      1 * span.setAttribute(Tags.PEER_HOST_IPV6, connection.address.hostAddress)
    }
    0 * _

    where:
    connection                                      | _
    new InetSocketAddress("localhost", 888)         | _
    new InetSocketAddress("ipv6.google.com", 999)   | _
    new InetSocketAddress("bad.address.local", 999) | _
  }

  def "test onError"() {
    when:
    decorator.onError(span, error)

    then:
    if (error) {
      1 * span.setStatus(Status.UNKNOWN)
      1 * span.setAttribute(MoreTags.ERROR_TYPE, error.getClass().getName())
      1 * span.setAttribute(MoreTags.ERROR_STACK, _)
    }
    0 * _

    where:
    error << [new Exception(), null]
  }

  def "test beforeFinish"() {
    when:
    decorator.beforeFinish(span)

    then:
    0 * _
  }

  def "test assert null span"() {
    when:
    decorator.afterStart((Span) null)

    then:
    thrown(AssertionError)

    when:
    decorator.onError((Span) null, null)

    then:
    thrown(AssertionError)

    when:
    decorator.onError((Span) null, null)

    then:
    thrown(AssertionError)

    when:
    decorator.onPeerConnection((Span) null, null)

    then:
    thrown(AssertionError)
  }

  def "test spanNameForMethod"() {
    when:
    def result = decorator.spanNameForMethod(method)

    then:
    result == "${name}.run"

    where:
    target                         | name
    SomeInnerClass                 | "SomeInnerClass"
    SomeNestedClass                | "SomeNestedClass"
    SampleJavaClass.anonymousClass | "SampleJavaClass\$1"

    method = target.getDeclaredMethod("run")
  }

  def "test inject"() {
    setup:
    def child = decorator.beginSpan("mock", span)
    def map = new HashMap<String, String>()

    when:
    decorator.inject(child.getContext(), map, new MapSetter())

    then:
    map["traceparent"] =~ /[0-9a-f]{2}-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}/
  }

  def "test extract"() {
    setup:
    def child = decorator.beginSpan("mock", span)
    def map = new HashMap<String, String>()
    decorator.inject(child.getContext(), map, new MapSetter())

    when:
    SpanContext context = decorator.extract(map, new MapGetter())

    then:
    context.traceId.toLowerBase16() =~ /[0-9a-f]{32}/
  }

  def newDecorator() {
    return new BaseDecorator() {

      @Override
      protected String getSpanType() {
        return "test-type"
      }

      @Override
      protected String getComponentName() {
        return "test-component"
      }
    }
  }

  class SomeInnerClass implements Runnable {
    void run() {
    }
  }

  static class SomeNestedClass implements Runnable {
    void run() {
    }
  }
}
