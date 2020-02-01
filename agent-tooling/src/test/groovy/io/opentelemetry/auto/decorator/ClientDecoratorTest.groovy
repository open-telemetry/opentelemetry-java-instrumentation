package io.opentelemetry.auto.decorator

import io.opentelemetry.auto.instrumentation.api.MoreTags
import io.opentelemetry.auto.instrumentation.api.Tags
import io.opentelemetry.trace.Span

class ClientDecoratorTest extends BaseDecoratorTest {

  def span = Mock(Span)

  def "test afterStart"() {
    setup:
    def decorator = newDecorator((String) serviceName)

    when:
    decorator.afterStart(span)

    then:
    if (serviceName != null) {
      1 * span.setAttribute(MoreTags.SERVICE_NAME, serviceName)
    }
    1 * span.setAttribute(Tags.COMPONENT, "test-component")
    1 * span.setAttribute(Tags.SPAN_KIND, "client")
    1 * span.setAttribute(MoreTags.SPAN_TYPE, decorator.getSpanType())
    _ * span.setAttribute(_, _) // Want to allow other calls from child implementations.
    0 * _

    where:
    serviceName << ["test-service", "other-service", null]
  }

  def "test beforeFinish"() {
    when:
    newDecorator("test-service").beforeFinish(span)

    then:
    0 * _
  }

  @Override
  def newDecorator() {
    return newDecorator("test-service")
  }

  def newDecorator(String serviceName) {
    return new ClientDecorator() {

      @Override
      protected String service() {
        return serviceName
      }

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
}
