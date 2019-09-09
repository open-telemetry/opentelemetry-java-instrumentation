import com.couchbase.client.java.Bucket
import com.couchbase.client.java.document.JsonDocument
import com.couchbase.client.java.document.json.JsonObject
import com.couchbase.client.java.query.N1qlQuery
import util.AbstractCouchbaseTest

class CouchbaseClientTest extends AbstractCouchbaseTest {

  def "test hasBucket #type"() {
    when:
    def hasBucket = manager.hasBucket(bucketSettings.name())

    then:
    assert hasBucket
    assertTraces(1) {
      trace(0, 1) {
        assertCouchbaseCall(it, 0, "ClusterManager.hasBucket")
      }
    }

    where:
    manager          | cluster          | bucketSettings
    couchbaseManager | couchbaseCluster | bucketCouchbase
    memcacheManager  | memcacheCluster  | bucketMemcache

    type = bucketSettings.type().name()
  }

  def "test upsert and get #type"() {
    when:
    // Connect to the bucket and open it
    Bucket bkt = cluster.openBucket(bucketSettings.name(), bucketSettings.password())

    // Create a JSON document and store it with the ID "helloworld"
    JsonObject content = JsonObject.create().put("hello", "world")
    def inserted = bkt.upsert(JsonDocument.create("helloworld", content))
    def found = bkt.get("helloworld")

    then:
    found == inserted
    found.content().getString("hello") == "world"

    assertTraces(2) {
      trace(0, 1) {
        assertCouchbaseCall(it, 0, "Bucket.upsert", bucketSettings.name())
      }
      trace(1, 1) {
        assertCouchbaseCall(it, 0, "Bucket.get", bucketSettings.name())
      }
    }

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
        assertCouchbaseCall(it, 0, "Bucket.query", bucketSettings.name())
      }
    }

    where:
    manager          | cluster          | bucketSettings
    couchbaseManager | couchbaseCluster | bucketCouchbase
    // Only couchbase buckets support queries.

    type = bucketSettings.type().name()
  }
}
