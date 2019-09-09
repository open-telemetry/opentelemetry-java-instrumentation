import com.couchbase.client.java.document.JsonDocument
import com.couchbase.client.java.document.json.JsonObject
import com.couchbase.client.java.query.N1qlQuery
import spock.util.concurrent.BlockingVariable
import util.AbstractCouchbaseTest

import static datadog.trace.agent.test.utils.TraceUtils.basicSpan
import static datadog.trace.agent.test.utils.TraceUtils.runUnderTrace

class CouchbaseAsyncClientTest extends AbstractCouchbaseTest {
  def "test hasBucket #type"() {
    setup:
    def hasBucket = new BlockingVariable<Boolean>()

    when:
    manager.hasBucket(bucketSettings.name()).subscribe({ result -> hasBucket.set(result) })

    then:
    assert hasBucket.get()
    assertTraces(1) {
      trace(0, 1) {
        assertCouchbaseCall(it, 0, "ClusterManager.hasBucket")
      }
    }

    where:
    manager               | cluster               | bucketSettings
    couchbaseAsyncManager | couchbaseAsyncCluster | bucketCouchbase
    memcacheAsyncManager  | memcacheAsyncCluster  | bucketMemcache

    type = bucketSettings.type().name()
  }

  def "test upsert #type"() {
    setup:
    JsonObject content = JsonObject.create().put("hello", "world")
    def inserted = new BlockingVariable<JsonDocument>()

    when:
    runUnderTrace("someTrace") {
      // Connect to the bucket and open it
      cluster.openBucket(bucketSettings.name(), bucketSettings.password()).subscribe({ bkt ->
        bkt.upsert(JsonDocument.create("helloworld", content)).subscribe({ result -> inserted.set(result) })
      })
    }

    then:
    inserted.get().content().getString("hello") == "world"

    assertTraces(1) {
      trace(0, 3) {
        basicSpan(it, 0, "someTrace")

        assertCouchbaseCall(it, 0, "Cluster.openBucket", null, span(0))
        assertCouchbaseCall(it, 0, "Bucket.upsert", bucketSettings.name(), span(0))
      }
    }

    where:
    manager               | cluster               | bucketSettings
    couchbaseAsyncManager | couchbaseAsyncCluster | bucketCouchbase
    memcacheAsyncManager  | memcacheAsyncCluster  | bucketMemcache

    type = bucketSettings.type().name()
  }

  def "test upsert and get #type"() {
    setup:
    JsonObject content = JsonObject.create().put("hello", "world")
    def inserted = new BlockingVariable<JsonDocument>()
    def found = new BlockingVariable<JsonDocument>()

    when:
    runUnderTrace("someTrace") {
      cluster.openBucket(bucketSettings.name(), bucketSettings.password()).subscribe({ bkt ->
        bkt.upsert(JsonDocument.create("helloworld", content))
          .subscribe({ result ->
            inserted.set(result)
            bkt.get("helloworld")
              .subscribe({ searchResult -> found.set(searchResult)
              })
          })
      })
    }

    // Create a JSON document and store it with the ID "helloworld"
    then:
    found.get() == inserted.get()
    found.get().content().getString("hello") == "world"

    assertTraces(1) {
      trace(0, 4) {
        basicSpan(it, 0, "someTrace")
        
        assertCouchbaseCall(it, 0, "Cluster.openBucket", null, span(0))
        assertCouchbaseCall(it, 0, "Bucket.upsert", bucketSettings.name(), span(0))
        assertCouchbaseCall(it, 0, "Bucket.get", bucketSettings.name(), span(0))
      }
    }

    where:
    manager               | cluster               | bucketSettings
    couchbaseAsyncManager | couchbaseAsyncCluster | bucketCouchbase
    memcacheAsyncManager  | memcacheAsyncCluster  | bucketMemcache

    type = bucketSettings.type().name()
  }

  def "test query"() {
    setup:
    def queryResult = new BlockingVariable<JsonObject>()

    when:
    // Mock expects this specific query.
    // See com.couchbase.mock.http.query.QueryServer.handleString.
    runUnderTrace("someTrace") {
      cluster.openBucket(bucketSettings.name(), bucketSettings.password()).subscribe({
        bkt ->
          bkt.query(N1qlQuery.simple("SELECT mockrow"))
            .flatMap({ query -> query.rows() })
            .single()
            .subscribe({ row -> queryResult.set(row.value()) })
      })
    }

    then:
    queryResult.get().get("row") == "value"

    assertTraces(1) {
      trace(0, 3) {
        basicSpan(it, 0, "someTrace")

        assertCouchbaseCall(it, 0, "Cluster.openBucket", null, span(0))
        assertCouchbaseCall(it, 0, "Bucket.query", bucketSettings.name(), span(0))
      }
    }

    where:
    manager               | cluster               | bucketSettings
    couchbaseAsyncManager | couchbaseAsyncCluster | bucketCouchbase
    // Only couchbase buckets support queries.

    type = bucketSettings.type().name()
  }
}
