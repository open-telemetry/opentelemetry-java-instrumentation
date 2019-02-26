package datadog.trace.agent.decorator

import datadog.trace.api.DDTags
import io.opentracing.Span
import io.opentracing.tag.Tags
import spock.lang.Shared
import spock.lang.Specification

import static datadog.trace.agent.test.utils.TraceUtils.withSystemProperty
import static io.opentracing.log.Fields.ERROR_OBJECT

class BaseDecoratorTest extends Specification {

  @Shared
  def decorator = newDecorator()

  def span = Mock(Span)

  def "test afterStart"() {
    when:
    decorator.afterStart(span)

    then:
    1 * span.setTag(DDTags.SPAN_TYPE, decorator.spanType())
    1 * span.setTag(Tags.COMPONENT.key, "test-component")
    _ * span.setTag(_, _) // Want to allow other calls from child implementations.
    0 * _
  }

  def "test onError"() {
    when:
    decorator.onError(span, error)

    then:
    if (error) {
      1 * span.setTag(Tags.ERROR.key, true)
      1 * span.log([(ERROR_OBJECT): error])
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
    decorator.afterStart(null)

    then:
    thrown(AssertionError)

    when:
    decorator.onError(null, null)

    then:
    thrown(AssertionError)

    when:
    decorator.beforeFinish(null)

    then:
    thrown(AssertionError)
  }

  def "test analytics rate default disabled"() {
    when:
    BaseDecorator dec = newDecorator(defaultEnabled)

    then:
    dec.traceAnalyticsEnabled == defaultEnabled
    dec.traceAnalyticsSampleRate == sampleRate

    where:
    defaultEnabled | sampleRate
    true           | 1.0
    false          | 1.0
  }

  def "test analytics rate enabled"() {
    when:
    BaseDecorator dec = withSystemProperty("dd.integration.${integName}.analytics.enabled", "true") {
      withSystemProperty("dd.integration.${integName}.analytics.sample-rate", "$sampleRate") {
        newDecorator(enabled)
      }
    }

    then:
    dec.traceAnalyticsEnabled == expectedEnabled
    dec.traceAnalyticsSampleRate == (Float) expectedRate

    where:
    enabled | integName | sampleRate | expectedEnabled | expectedRate
    false   | ""        | ""         | false           | 1.0
    true    | ""        | ""         | true            | 1.0
    false   | "test1"   | 0.5        | true            | 0.5
    false   | "test2"   | 0.75       | true            | 0.75
    true    | "test1"   | 0.2        | true            | 0.2
    true    | "test2"   | 0.4        | true            | 0.4
    true    | "test1"   | ""         | true            | 1.0
    true    | "test2"   | ""         | true            | 1.0
  }

  def newDecorator() {
    return newDecorator(false)
  }

  def newDecorator(final Boolean analyticsEnabledDefault) {
    return analyticsEnabledDefault ?
      new BaseDecorator() {
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
      } :
      new BaseDecorator() {
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
      }
  }
}
