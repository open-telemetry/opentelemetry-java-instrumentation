/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.instrumentation.test.utils.TraceUtils.basicSpan
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.runUnderTrace

import com.couchbase.client.java.Bucket
import com.couchbase.client.java.Cluster
import com.couchbase.client.java.CouchbaseCluster
import com.couchbase.client.java.document.JsonDocument
import com.couchbase.client.java.document.json.JsonObject
import com.couchbase.client.java.env.CouchbaseEnvironment
import com.couchbase.client.java.query.N1qlQuery
import spock.lang.Unroll
import util.AbstractCouchbaseTest

@Unroll
abstract class AbstractCouchbaseClientTest extends AbstractCouchbaseTest {
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

    cleanup:
    cluster?.disconnect()
    environment.shutdown()

    where:
    bucketSettings << [bucketCouchbase, bucketMemcache]

    environment = envBuilder(bucketSettings).build()
    cluster = CouchbaseCluster.create(environment, Arrays.asList("127.0.0.1"))
    manager = cluster.clusterManager(USERNAME, PASSWORD)
    type = bucketSettings.type().name()
  }

  def "test upsert and get #type"() {
    when:
    // Connect to the bucket and open it
    Bucket bkt = cluster.openBucket(bucketSettings.name(), bucketSettings.password())

    // Create a JSON document and store it with the ID "helloworld"
    JsonObject content = JsonObject.create().put("hello", "world")

    def inserted
    def found

    runUnderTrace("someTrace") {
      inserted = bkt.upsert(JsonDocument.create("helloworld", content))
      found = bkt.get("helloworld")
    }

    then:
    found == inserted
    found.content().getString("hello") == "world"

    assertTraces(2) {
      trace(0, 1) {
        assertCouchbaseCall(it, 0, "Cluster.openBucket")
      }
      trace(1, 3) {
        basicSpan(it, 0, "someTrace")
        assertCouchbaseCall(it, 1, "Bucket.upsert", bucketSettings.name(), span(0))
        assertCouchbaseCall(it, 2, "Bucket.get", bucketSettings.name(), span(0))
      }
    }

    cleanup:
    cluster?.disconnect()
    environment.shutdown()

    where:
    bucketSettings << [bucketCouchbase, bucketMemcache]

    environment = envBuilder(bucketSettings).build()
    cluster = CouchbaseCluster.create(environment, Arrays.asList("127.0.0.1"))
    type = bucketSettings.type().name()
  }

  def "test query"() {
    setup:
    // Only couchbase buckets support queries.
    CouchbaseEnvironment environment = envBuilder(bucketCouchbase).build()
    Cluster cluster = CouchbaseCluster.create(environment, Arrays.asList("127.0.0.1"))
    Bucket bkt = cluster.openBucket(bucketCouchbase.name(), bucketCouchbase.password())

    when:
    // Mock expects this specific query.
    // See com.couchbase.mock.http.query.QueryServer.handleString.
    def result = bkt.query(N1qlQuery.simple("SELECT mockrow"))

    then:
    result.parseSuccess()
    result.finalSuccess()
    result.first().value().get("row") == "value"

    and:
    assertTraces(2) {
      trace(0, 1) {
        assertCouchbaseCall(it, 0, "Cluster.openBucket")
      }
      trace(1, 1) {
        def dbName = bucketCouchbase.name()
        assertCouchbaseCall(it, 0, "SELECT $dbName", dbName, null, 'SELECT mockrow')
      }
    }

    cleanup:
    cluster?.disconnect()
    environment.shutdown()
  }
}
