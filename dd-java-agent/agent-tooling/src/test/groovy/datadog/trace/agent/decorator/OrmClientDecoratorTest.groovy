package datadog.trace.agent.decorator

import datadog.trace.api.DDTags
import io.opentracing.Span

class OrmClientDecoratorTest extends DatabaseClientDecoratorTest {

  def span = Mock(Span)

  def "test onOperation #testName"() {
    setup:
    decorator = newDecorator({ e -> entityName })

    when:
    decorator.onOperation(span, entity)

    then:
    if (isSet) {
      1 * span.setTag(DDTags.RESOURCE_NAME, entityName)
    }
    0 * _

    where:
    testName          | entity     | entityName | isSet
    "null entity"     | null       | "name"     | false
    "null entityName" | "not null" | null       | false
    "name set"        | "not null" | "name"     | true
  }

  def "test onOperation null span"() {
    setup:
    decorator = newDecorator({ e -> null })

    when:
    decorator.onOperation(null, null)

    then:
    thrown(AssertionError)
  }


  def newDecorator(name) {
    return new OrmClientDecorator() {
      @Override
      String entityName(Object entity) {
        return name.call(entity)
      }

      @Override
      protected String dbType() {
        return "test-db"
      }

      @Override
      protected String dbUser(Object o) {
        return "test-user"
      }

      @Override
      protected String dbInstance(Object o) {
        return "test-user"
      }

      @Override
      protected String service() {
        return "test-service"
      }

      @Override
      protected String[] instrumentationNames() {
        return ["test1"]
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
