package datadog.trace.agent.decorator

import datadog.trace.api.DDTags
import io.opentracing.Span
import io.opentracing.tag.Tags

class ServerDecoratorTest extends BaseDecoratorTest {

  def span = Mock(Span)

  def "test afterStart"() {
    def decorator = newDecorator()
    when:
    decorator.afterStart(span)

    then:
    1 * span.setTag(Tags.COMPONENT.key, "test-component")
    1 * span.setTag(Tags.SPAN_KIND.key, "server")
    1 * span.setTag(DDTags.SPAN_TYPE, decorator.spanType())
    if (decorator.traceAnalyticsEnabled) {
      1 * span.setTag(DDTags.ANALYTICS_SAMPLE_RATE, 1.0)
    }
    0 * _
  }

  def "test beforeFinish"() {
    when:
    newDecorator().beforeFinish(span)

    then:
    0 * _
  }

  @Override
  def newDecorator() {
    return new ServerDecorator() {
      @Override
      protected String[] instrumentationNames() {
        return ["test1", "test2"]
      }

      @Override
      protected String spanType() {
        return "test-type"
      }

      @Override
      protected String component() {
        return "test-component"
      }

      protected boolean traceAnalyticsDefault() {
        return true
      }
    }
  }
}
