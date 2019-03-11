import com.couchbase.client.java.Bucket
import com.couchbase.client.java.document.JsonDocument
import com.couchbase.client.java.document.json.JsonObject
import com.couchbase.client.java.query.N1qlQuery
import datadog.trace.api.DDSpanTypes
import io.opentracing.tag.Tags
import util.AbstractCouchbaseTest

class CouchbaseClientTest extends AbstractCouchbaseTest {

  def "test client #type"() {
    when:
    manager.hasBucket(bucketSettings.name())

    then:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          serviceName "couchbase"
          resourceName "ClusterManager.hasBucket"
          operationName "couchbase.call"
          spanType DDSpanTypes.COUCHBASE
          errored false
          parent()
          tags {
            "$Tags.COMPONENT.key" "couchbase-client"
            "$Tags.DB_TYPE.key" "couchbase"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            defaultTags()
          }
        }
      }
    }
    TEST_WRITER.clear()

    when:
    // Connect to the bucket and open it
    Bucket bkt = cluster.openBucket(bucketSettings.name(), bucketSettings.password())

    // Create a JSON document and store it with the ID "helloworld"
    JsonObject content = JsonObject.create().put("hello", "world")
    def inserted = bkt.upsert(JsonDocument.create("helloworld", content))

    then:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          serviceName "couchbase"
          resourceName "Bucket.upsert"
          operationName "couchbase.call"
          spanType DDSpanTypes.COUCHBASE
          errored false
          parent()
          tags {
            "$Tags.COMPONENT.key" "couchbase-client"
            "$Tags.DB_TYPE.key" "couchbase"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "bucket" bkt.name()
            defaultTags()
          }
        }
      }
    }
    TEST_WRITER.clear()

    when:
    def found = bkt.get("helloworld")

    then:
    found == inserted
    found.content().getString("hello") == "world"

    and:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          serviceName "couchbase"
          resourceName "Bucket.get"
          operationName "couchbase.call"
          spanType DDSpanTypes.COUCHBASE
          errored false
          parent()
          tags {
            "$Tags.COMPONENT.key" "couchbase-client"
            "$Tags.DB_TYPE.key" "couchbase"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "bucket" bkt.name()
            defaultTags()
          }
        }
      }
    }
    TEST_WRITER.clear()


    where:
    manager          | cluster          | bucketSettings
    couchbaseManager | couchbaseCluster | bucketCouchbase
    memcacheManager  | memcacheCluster  | bucketMemcache

    type = bucketSettings.type().name()
  }

  def "test query"() {
    setup:
    Bucket bkt = cluster.openBucket(bucketSettings.name(), bucketSettings.password())

    when:
    // Mock expects this specific query.
    // See com.couchbase.mock.http.query.QueryServer.handleString.
    def result = bkt.query(N1qlQuery.simple("SELECT mockrow"))

    then:
    result.parseSuccess()
    result.finalSuccess()
    result.first().value().get("row") == "value"

    and:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          serviceName "couchbase"
          resourceName "Bucket.query"
          operationName "couchbase.call"
          spanType DDSpanTypes.COUCHBASE
          errored false
          parent()
          tags {
            "$Tags.COMPONENT.key" "couchbase-client"
            "$Tags.DB_TYPE.key" "couchbase"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "bucket" bkt.name()
            defaultTags()
          }
        }
      }
    }

    where:
    manager          | cluster          | bucketSettings
    couchbaseManager | couchbaseCluster | bucketCouchbase
    // Only couchbase buckets support queries.

    type = bucketSettings.type().name()
  }
}
