/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import com.couchbase.client.java.AsyncCluster
import com.couchbase.client.java.CouchbaseAsyncCluster
import com.couchbase.client.java.document.JsonDocument
import com.couchbase.client.java.document.json.JsonObject
import com.couchbase.client.java.env.CouchbaseEnvironment
import com.couchbase.client.java.query.N1qlQuery
import io.opentelemetry.api.trace.SpanKind
import spock.lang.Unroll
import spock.util.concurrent.BlockingVariable
import util.AbstractCouchbaseTest

import java.util.concurrent.TimeUnit

@Unroll
abstract class AbstractCouchbaseAsyncClientTest extends AbstractCouchbaseTest {
  static final int TIMEOUT = 10

  def "test hasBucket #type"() {
    setup:
    def hasBucket = new BlockingVariable<Boolean>(TIMEOUT)

    when:
    cluster.openBucket(bucketSettings.name(), bucketSettings.password()).subscribe({ bkt ->
      manager.hasBucket(bucketSettings.name()).subscribe({ result -> hasBucket.set(result) })
    })

    then:
    assert hasBucket.get()
    assertTraces(1) {
      trace(0, 2) {
        assertCouchbaseCall(it, 0, "Cluster.openBucket")
        assertCouchbaseCall(it, 1, "ClusterManager.hasBucket", span(0))
      }
    }

    cleanup:
    cluster?.disconnect()?.timeout(TIMEOUT, TimeUnit.SECONDS)?.toBlocking()?.single()
    environment.shutdown()

    where:
    bucketSettings << [bucketCouchbase, bucketMemcache]

    environment = envBuilder(bucketSettings).build()
    cluster = CouchbaseAsyncCluster.create(environment, Arrays.asList("127.0.0.1"))
    manager = cluster.clusterManager(USERNAME, PASSWORD).toBlocking().single()
    type = bucketSettings.type().name()
  }

  def "test upsert #type"() {
    setup:
    JsonObject content = JsonObject.create().put("hello", "world")
    def inserted = new BlockingVariable<JsonDocument>(TIMEOUT)

    when:
    runWithSpan("someTrace") {
      // Connect to the bucket and open it
      cluster.openBucket(bucketSettings.name(), bucketSettings.password()).subscribe({ bkt ->
        bkt.upsert(JsonDocument.create("helloworld", content)).subscribe({ result -> inserted.set(result) })
      })
    }

    then:
    inserted.get().content().getString("hello") == "world"

    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          name "someTrace"
          kind SpanKind.INTERNAL
          hasNoParent()
        }

        assertCouchbaseCall(it, 1, "Cluster.openBucket", span(0))
        assertCouchbaseCall(it, 2, "Bucket.upsert", span(1), bucketSettings.name())
      }
    }

    cleanup:
    cluster?.disconnect()?.timeout(TIMEOUT, TimeUnit.SECONDS)?.toBlocking()?.single()
    environment.shutdown()

    where:
    bucketSettings << [bucketCouchbase, bucketMemcache]

    environment = envBuilder(bucketSettings).build()
    cluster = CouchbaseAsyncCluster.create(environment, Arrays.asList("127.0.0.1"))
    type = bucketSettings.type().name()
  }

  def "test upsert and get #type"() {
    setup:
    JsonObject content = JsonObject.create().put("hello", "world")
    def inserted = new BlockingVariable<JsonDocument>(TIMEOUT)
    def found = new BlockingVariable<JsonDocument>(TIMEOUT)

    when:
    runWithSpan("someTrace") {
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
        span(0) {
          name "someTrace"
          kind SpanKind.INTERNAL
          hasNoParent()
        }

        assertCouchbaseCall(it, 1, "Cluster.openBucket", span(0))
        assertCouchbaseCall(it, 2, "Bucket.upsert", span(1), bucketSettings.name())
        assertCouchbaseCall(it, 3, "Bucket.get", span(2), bucketSettings.name())
      }
    }

    cleanup:
    cluster?.disconnect()?.timeout(TIMEOUT, TimeUnit.SECONDS)?.toBlocking()?.single()
    environment.shutdown()

    where:
    bucketSettings << [bucketCouchbase, bucketMemcache]

    environment = envBuilder(bucketSettings).build()
    cluster = CouchbaseAsyncCluster.create(environment, Arrays.asList("127.0.0.1"))
    type = bucketSettings.type().name()
  }

  def "test query"() {
    setup:
    // Only couchbase buckets support queries.
    CouchbaseEnvironment environment = envBuilder(bucketCouchbase).build()
    AsyncCluster cluster = CouchbaseAsyncCluster.create(environment, Arrays.asList("127.0.0.1"))
    def queryResult = new BlockingVariable<JsonObject>(TIMEOUT)

    when:
    // Mock expects this specific query.
    // See com.couchbase.mock.http.query.QueryServer.handleString.
    runWithSpan("someTrace") {
      cluster.openBucket(bucketCouchbase.name(), bucketCouchbase.password()).subscribe({
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
        span(0) {
          name "someTrace"
          kind SpanKind.INTERNAL
          hasNoParent()
        }

        assertCouchbaseCall(it, 1, "Cluster.openBucket", span(0))

        def dbName = bucketCouchbase.name()
        assertCouchbaseCall(it, 2, "SELECT $dbName", span(1), dbName, 'SELECT mockrow', 'SELECT')
      }
    }

    cleanup:
    cluster?.disconnect()?.timeout(TIMEOUT, TimeUnit.SECONDS)?.toBlocking()?.single()
    environment.shutdown()
  }
}
