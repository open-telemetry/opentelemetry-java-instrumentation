package springdata

import com.couchbase.client.java.Bucket
import org.springframework.data.couchbase.core.CouchbaseTemplate
import spock.lang.Shared
import util.AbstractCouchbaseTest

import static datadog.trace.agent.test.asserts.ListWriterAssert.assertTraces

class CouchbaseSpringTemplateTest extends AbstractCouchbaseTest {

  @Shared
  List<CouchbaseTemplate> templates

  def setupSpec() {
    Bucket bucketCouchbase = cluster.openBucket(bucketCouchbase.name())
    Bucket bucketMemcache = cluster.openBucket(bucketMemcache.name())
    Bucket bucketEphemeral = cluster.openBucket(bucketEphemeral.name())
    def info = manager.info()

    templates = [new CouchbaseTemplate(info, bucketCouchbase),
                 new CouchbaseTemplate(info, bucketMemcache),
                 new CouchbaseTemplate(info, bucketEphemeral)]
  }

  def "test write/read #name"() {
    setup:
    def doc = new Doc()

    when:
    template.save(doc)

    then:
    template.findById("1", Doc) != null

    when:
    template.remove(doc)

    then:
    template.findById("1", Doc) == null

    and:
    assertTraces(TEST_WRITER, 4) {
      trace(0, 1) {
        span(0) {
          serviceName "couchbase"
          resourceName "Bucket.upsert($name)"
          operationName "couchbase.call"
          errored false
          parent()
          tags {
            "bucket" name
            defaultTags()
          }
        }
      }
      trace(1, 1) {
        span(0) {
          serviceName "couchbase"
          resourceName "Bucket.get($name)"
          operationName "couchbase.call"
          errored false
          parent()
          tags {
            "bucket" name
            defaultTags()
          }
        }
      }
      trace(2, 1) {
        span(0) {
          serviceName "couchbase"
          resourceName "Bucket.remove($name)"
          operationName "couchbase.call"
          errored false
          parent()
          tags {
            "bucket" name
            defaultTags()
          }
        }
      }
      trace(3, 1) {
        span(0) {
          serviceName "couchbase"
          resourceName "Bucket.get($name)"
          operationName "couchbase.call"
          errored false
          parent()
          tags {
            "bucket" name
            defaultTags()
          }
        }
      }
    }

    where:
    template << templates
    name = template.couchbaseBucket.name()
  }
}
