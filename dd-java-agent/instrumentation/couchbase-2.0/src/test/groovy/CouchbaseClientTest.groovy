import com.couchbase.client.java.Bucket
import com.couchbase.client.java.document.JsonDocument
import com.couchbase.client.java.document.json.JsonObject
import util.AbstractCouchbaseTest

import static datadog.trace.agent.test.asserts.ListWriterAssert.assertTraces

class CouchbaseClientTest extends AbstractCouchbaseTest {

  def "test client #type"() {
    setup:
    // Connect to the bucket and open it
    Bucket bkt = cluster.openBucket(bucketSettings.name())

    // Create a JSON document and store it with the ID "helloworld"
    JsonObject content = JsonObject.create().put("hello", "world")

    when:
    def inserted = bkt.upsert(JsonDocument.create("helloworld", content))
    def found = bkt.get("helloworld")

    then:
    found == inserted
    found.content().getString("hello") == "world"

    and:
    assertTraces(TEST_WRITER, 2) {
      trace(0, 1) {
        span(0) {
          serviceName "couchbase"
          resourceName "Bucket.upsert"
          operationName "couchbase.call"
          errored false
          parent()
          tags {
            defaultTags()
          }
        }
      }
      trace(1, 1) {
        span(0) {
          serviceName "couchbase"
          resourceName "Bucket.get"
          operationName "couchbase.call"
          errored false
          parent()
          tags {
            defaultTags()
          }
        }
      }
    }

    where:
    bucketSettings << [BUCKET_COUCHBASE, BUCKET_MEMCACHE, BUCKET_EPHEMERAL]
    type = bucketSettings.type().name()
  }
}
