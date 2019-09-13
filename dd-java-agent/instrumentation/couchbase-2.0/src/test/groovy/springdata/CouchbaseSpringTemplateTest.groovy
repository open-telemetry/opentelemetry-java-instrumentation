package springdata

import com.couchbase.client.java.Bucket
import com.couchbase.client.java.Cluster
import com.couchbase.client.java.CouchbaseCluster
import com.couchbase.client.java.cluster.ClusterManager
import com.couchbase.client.java.env.CouchbaseEnvironment
import org.springframework.data.couchbase.core.CouchbaseTemplate
import spock.lang.Shared
import util.AbstractCouchbaseTest

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

    templates = [new CouchbaseTemplate(couchbaseManager.info(), bucketCouchbase),
                 new CouchbaseTemplate(memcacheManager.info(), bucketMemcache)]
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

    when:
    template.save(doc)
    def result = template.findById("1", Doc)

    then:
    result != null
    assertTraces(2) {
      trace(0, 1) {
        assertCouchbaseCall(it, 0, "Bucket.upsert", name)
      }
      trace(1, 1) {
        assertCouchbaseCall(it, 0, "Bucket.get", name)
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
    template.save(doc)
    template.remove(doc)

    then:
    assertTraces(2) {
      trace(0, 1) {
        assertCouchbaseCall(it, 0, "Bucket.upsert", name)
      }
      trace(1, 1) {
        assertCouchbaseCall(it, 0, "Bucket.remove", name)
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
