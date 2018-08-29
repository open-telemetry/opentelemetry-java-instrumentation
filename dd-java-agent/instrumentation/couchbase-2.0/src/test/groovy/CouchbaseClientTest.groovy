import com.couchbase.client.java.Bucket
import com.couchbase.client.java.document.JsonDocument
import com.couchbase.client.java.document.json.JsonObject
import com.couchbase.client.java.query.N1qlQuery
import com.couchbase.client.java.query.N1qlQueryRow
import util.AbstractCouchbaseTest

import static datadog.trace.agent.test.asserts.ListWriterAssert.assertTraces

class CouchbaseClientTest extends AbstractCouchbaseTest {

  def "test client #type"() {
    when:
    manager.hasBucket(bucketSettings.name())

    then:
    assertTraces(TEST_WRITER, 1) {
      trace(0, 1) {
        span(0) {
          serviceName "couchbase"
          resourceName "ClusterManager.hasBucket"
          operationName "couchbase.call"
          errored false
          parent()
          tags {
            defaultTags()
          }
        }
      }
    }
    TEST_WRITER.clear()

    when:
    // Connect to the bucket and open it
    Bucket bkt = cluster.openBucket(bucketSettings.name())

    // Create a JSON document and store it with the ID "helloworld"
    JsonObject content = JsonObject.create().put("hello", "world")
    def inserted = bkt.upsert(JsonDocument.create("helloworld", content))

    then:
    assertTraces(TEST_WRITER, 1) {
      trace(0, 1) {
        span(0) {
          serviceName "couchbase"
          resourceName "Bucket.upsert(${bkt.name()})"
          operationName "couchbase.call"
          errored false
          parent()
          tags {
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
    assertTraces(TEST_WRITER, 1) {
      trace(0, 1) {
        span(0) {
          serviceName "couchbase"
          resourceName "Bucket.get(${bkt.name()})"
          operationName "couchbase.call"
          errored false
          parent()
          tags {
            "bucket" bkt.name()
            defaultTags()
          }
        }
      }
    }
    TEST_WRITER.clear()


    where:
    bucketSettings << [bucketCouchbase, bucketMemcache, bucketEphemeral]
    type = bucketSettings.type().name()
  }

  def "test query"() {
    when:
    // Connect to the bucket and open it
    Bucket bkt = cluster.openBucket(bucketSettings.name())

    // Create a JSON document and store it with the ID "helloworld"
    JsonObject content = JsonObject.create().put("hello", "world")
    def inserted = bkt.upsert(JsonDocument.create("helloworld", content))

    then:
    inserted != null
    assertTraces(TEST_WRITER, 1) {
      trace(0, 1) {
        span(0) {
          serviceName "couchbase"
          resourceName "Bucket.upsert(${bkt.name()})"
          operationName "couchbase.call"
          errored false
          parent()
          tags {
            "bucket" bkt.name()
            defaultTags()
          }
        }
      }
    }
    TEST_WRITER.clear()

    when:
    def result = bkt.query(
      N1qlQuery.parameterized(
        "SELECT * FROM `${bkt.name()}` WHERE hello = \$hello",
        JsonObject.create()
          .put("hello", "world")
      )
    )

    then:
    result.parseSuccess()
    result.finalSuccess()
    N1qlQueryRow row = result.first()
    row.value().get(bkt.name()).get("hello") == "world"

    and:
    assertTraces(TEST_WRITER, 1) {
      trace(0, 1) {
        span(0) {
          serviceName "couchbase"
          resourceName "Bucket.query(${bkt.name()})"
          operationName "couchbase.call"
          errored false
          parent()
          tags {
            "bucket" bkt.name()
            defaultTags()
          }
        }
      }
    }

    where:
    bucketSettings << [bucketCouchbase] // Only couchbase buckets support queries.
    type = bucketSettings.type().name()
  }
}
