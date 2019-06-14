package datadog.trace.agent.decorator

import datadog.trace.api.Config
import datadog.trace.api.DDTags
import io.opentracing.Span
import io.opentracing.tag.Tags

import static datadog.trace.agent.test.utils.ConfigUtils.withConfigOverride

class DatabaseClientDecoratorTest extends ClientDecoratorTest {

  def span = Mock(Span)

  def "test afterStart"() {
    setup:
    def decorator = newDecorator((String) serviceName)

    when:
    decorator.afterStart(span)

    then:
    if (serviceName != null) {
      1 * span.setTag(DDTags.SERVICE_NAME, serviceName)
    }
    1 * span.setTag(Tags.COMPONENT.key, "test-component")
    1 * span.setTag(Tags.SPAN_KIND.key, "client")
    1 * span.setTag(Tags.DB_TYPE.key, "test-db")
    1 * span.setTag(DDTags.SPAN_TYPE, "test-type")
    1 * span.setTag(DDTags.ANALYTICS_SAMPLE_RATE, 1.0)
    0 * _

    where:
    serviceName << ["test-service", "other-service", null]
  }

  def "test onConnection"() {
    setup:
    def decorator = newDecorator()

    when:
    withConfigOverride(Config.DB_CLIENT_HOST_SPLIT_BY_INSTANCE, "$renameService") {
      decorator.onConnection(span, session)
    }

    then:
    if (session) {
      1 * span.setTag(Tags.DB_USER.key, session.user)
      1 * span.setTag(Tags.DB_INSTANCE.key, session.instance)
      if (renameService && session.instance) {
        1 * span.setTag(DDTags.SERVICE_NAME, session.instance)
      }
    }
    0 * _

    where:
    renameService | session
    false         | null
    true          | [user: "test-user"]
    false         | [instance: "test-instance"]
    true          | [user: "test-user", instance: "test-instance"]
  }

  def "test onStatement"() {
    setup:
    def decorator = newDecorator()

    when:
    decorator.onStatement(span, statement)

    then:
    1 * span.setTag(Tags.DB_STATEMENT.key, statement)
    0 * _

    where:
    statement      | _
    null           | _
    ""             | _
    "db-statement" | _
  }

  def "test assert null span"() {
    setup:
    def decorator = newDecorator()

    when:
    decorator.afterStart((Span) null)

    then:
    thrown(AssertionError)

    when:
    decorator.onConnection(null, null)

    then:
    thrown(AssertionError)

    when:
    decorator.onStatement(null, null)

    then:
    thrown(AssertionError)
  }

  @Override
  def newDecorator(String serviceName = "test-service") {
    return new DatabaseClientDecorator<Map>() {
      @Override
      protected String[] instrumentationNames() {
        return ["test1", "test2"]
      }

      @Override
      protected String service() {
        return serviceName
      }

      @Override
      protected String component() {
        return "test-component"
      }

      @Override
      protected String spanType() {
        return "test-type"
      }

      @Override
      protected String dbType() {
        return "test-db"
      }

      @Override
      protected String dbUser(Map map) {
        return map.user
      }

      @Override
      protected String dbInstance(Map map) {
        return map.instance
      }

      protected boolean traceAnalyticsDefault() {
        return true
      }
    }
  }
}
