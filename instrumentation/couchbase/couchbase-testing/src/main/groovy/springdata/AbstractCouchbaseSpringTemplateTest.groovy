/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package springdata

import static io.opentelemetry.instrumentation.test.utils.TraceUtils.basicSpan
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.runUnderTrace

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

@Retry(count = 10, delay = 500)
@Unroll
class AbstractCouchbaseSpringTemplateTest extends AbstractCouchbaseTest {

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
    clearExportedData()

    when:
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
