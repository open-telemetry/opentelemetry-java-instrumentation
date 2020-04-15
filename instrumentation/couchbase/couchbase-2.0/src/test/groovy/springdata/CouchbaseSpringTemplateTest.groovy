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
package springdata

import com.couchbase.client.java.Bucket
import com.couchbase.client.java.Cluster
import com.couchbase.client.java.CouchbaseCluster
import com.couchbase.client.java.cluster.ClusterManager
import com.couchbase.client.java.env.CouchbaseEnvironment
import org.springframework.data.couchbase.core.CouchbaseTemplate
import spock.lang.Retry
import spock.lang.Shared
import spock.lang.Unroll
import util.AbstractCouchbaseTest

import static io.opentelemetry.auto.test.utils.TraceUtils.basicSpan
import static io.opentelemetry.auto.test.utils.TraceUtils.runUnderTrace

@Retry(count = 10, delay = 5)
@Unroll
class CouchbaseSpringTemplateTest extends AbstractCouchbaseTest {

  @Shared
  List<CouchbaseTemplate> templates

  @Shared
  Cluster couchbaseCluster

  @Shared
  Cluster memcacheCluster

  @Shared
  protected CouchbaseEnvironment couchbaseEnvironment
  @Shared
  protected CouchbaseEnvironment memcacheEnvironment

  def setupSpec() {
    couchbaseEnvironment = envBuilder(bucketCouchbase).build()
    memcacheEnvironment = envBuilder(bucketMemcache).build()

    couchbaseCluster = CouchbaseCluster.create(couchbaseEnvironment, Arrays.asList("127.0.0.1"))
    memcacheCluster = CouchbaseCluster.create(memcacheEnvironment, Arrays.asList("127.0.0.1"))
    ClusterManager couchbaseManager = couchbaseCluster.clusterManager(USERNAME, PASSWORD)
    ClusterManager memcacheManager = memcacheCluster.clusterManager(USERNAME, PASSWORD)

    Bucket bucketCouchbase = couchbaseCluster.openBucket(bucketCouchbase.name(), bucketCouchbase.password())
    Bucket bucketMemcache = memcacheCluster.openBucket(bucketMemcache.name(), bucketMemcache.password())

    runUnderTrace("getting info") {
      templates = [new CouchbaseTemplate(couchbaseManager.info(), bucketCouchbase),
                   new CouchbaseTemplate(memcacheManager.info(), bucketMemcache)]
    }
  }

  def cleanupSpec() {
    couchbaseCluster?.disconnect()
    memcacheCluster?.disconnect()
    couchbaseEnvironment.shutdown()
    memcacheEnvironment.shutdown()
  }

  def "test write #name"() {
    setup:
    def doc = new Doc()
    def result

    when:
    runUnderTrace("someTrace") {
      template.save(doc)
      result = template.findById("1", Doc)
    }


    then:
    result != null

    assertTraces(1) {
      trace(0, 3) {
        basicSpan(it, 0, "someTrace")
        assertCouchbaseCall(it, 1, "Bucket.upsert", name, span(0))
        assertCouchbaseCall(it, 2, "Bucket.get", name, span(0))
      }
    }

    where:
    template << templates
    name = template.couchbaseBucket.name()
  }

  def "test remove #name"() {
    setup:
    def doc = new Doc()

    when:
    runUnderTrace("someTrace") {
      template.save(doc)
      template.remove(doc)
    }


    then:
    assertTraces(1) {
      trace(0, 3) {
        basicSpan(it, 0, "someTrace")
        assertCouchbaseCall(it, 1, "Bucket.upsert", name, span(0))
        assertCouchbaseCall(it, 2, "Bucket.remove", name, span(0))
      }
    }

    when:
    TEST_WRITER.clear()
    def result = template.findById("1", Doc)

    then:
    result == null
    assertTraces(1) {
      trace(0, 1) {
        assertCouchbaseCall(it, 0, "Bucket.get", name)
      }
    }

    where:
    template << templates
    name = template.couchbaseBucket.name()
  }
}
