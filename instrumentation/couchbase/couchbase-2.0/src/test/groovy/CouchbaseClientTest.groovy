/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.couchbase.client.java.Bucket
import com.couchbase.client.java.Cluster
import com.couchbase.client.java.CouchbaseCluster
import com.couchbase.client.java.document.JsonDocument
import com.couchbase.client.java.document.json.JsonObject
import com.couchbase.client.java.env.CouchbaseEnvironment
import com.couchbase.client.java.query.N1qlQuery
import spock.lang.Retry
import spock.lang.Unroll
import util.AbstractCouchbaseTest

import static io.opentelemetry.auto.test.utils.TraceUtils.basicSpan
import static io.opentelemetry.auto.test.utils.TraceUtils.runUnderTrace

@Retry
@Unroll
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
        assertCouchbaseCall(it, 0, 'SimpleN1qlQuery{statement=SELECT mockrow}', bucketCouchbase.name())
      }
    }

    cleanup:
    cluster?.disconnect()
    environment.shutdown()
  }
}
