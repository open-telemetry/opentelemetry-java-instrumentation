/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package springdata


import com.couchbase.client.java.Bucket
import com.couchbase.client.java.Cluster
import com.couchbase.client.java.CouchbaseCluster
import com.couchbase.client.java.cluster.ClusterManager
import com.couchbase.client.java.env.CouchbaseEnvironment
import io.opentelemetry.api.trace.SpanKind
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

    runWithSpan("getting info") {
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

  def "test write #testName"() {
    setup:
    def doc = new Doc()
    def result

    when:
    runWithSpan("someTrace") {
      template.save(doc)
      result = template.findById("1", Doc)
    }


    then:
    result != null

    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          name "someTrace"
          kind SpanKind.INTERNAL
          hasNoParent()
        }
        assertCouchbaseCall(it, 1, "Bucket.upsert", span(0), testName)
        assertCouchbaseCall(it, 2, "Bucket.get", span(0), testName)
      }
    }

    where:
    template << templates
    testName = template.couchbaseBucket.name()
  }

  def "test remove #testName"() {
    setup:
    def doc = new Doc()

    when:
    runWithSpan("someTrace") {
      template.save(doc)
      template.remove(doc)
    }


    then:
    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          name "someTrace"
          kind SpanKind.INTERNAL
          hasNoParent()
        }
        assertCouchbaseCall(it, 1, "Bucket.upsert", span(0), testName)
        assertCouchbaseCall(it, 2, "Bucket.remove", span(0), testName)
      }
    }
    clearExportedData()

    when:
    def result = template.findById("1", Doc)

    then:
    result == null
    assertTraces(1) {
      trace(0, 1) {
        assertCouchbaseCall(it, 0, "Bucket.get", null, testName)
      }
    }

    where:
    template << templates
    testName = template.couchbaseBucket.name()
  }
}
