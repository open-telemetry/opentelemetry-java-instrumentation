import datadog.trace.api.DDTags
import io.opentracing.Span
import io.opentracing.tag.Tags
import org.bson.BsonArray
import org.bson.BsonDocument
import org.bson.BsonString
import spock.lang.Shared
import spock.lang.Specification

import static datadog.trace.instrumentation.mongo.MongoClientDecorator.DECORATE

class MongoClientDecoratorTest extends Specification {

  @Shared
  def query1, query2

  def setupSpec() {
    query1 = new BsonDocument("find", new BsonString("show"))
    query1.put("stuff", new BsonString("secret"))


    query2 = new BsonDocument("insert", new BsonString("table"))
    def nestedDoc = new BsonDocument("count", new BsonString("show"))
    nestedDoc.put("id", new BsonString("secret"))
    query2.put("docs", new BsonArray(Arrays.asList(new BsonString("secret"), nestedDoc)))
  }

  def "test query scrubbing"() {
    setup:
    def span = Mock(Span)
    // all "secret" strings should be scrubbed out of these queries

    when:
    DECORATE.onStatement(span, query)

    then:
    1 * span.setTag(Tags.DB_STATEMENT.key, expected)
    1 * span.setTag(DDTags.RESOURCE_NAME, expected)
    0 * _

    where:
    query << [query1, query2]
    expected = query.toString().replaceAll("secret", "?")
  }
}
