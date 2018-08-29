package springdata


import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.data.repository.CrudRepository
import spock.lang.Shared
import util.AbstractCouchbaseTest

import static datadog.trace.agent.test.asserts.ListWriterAssert.assertTraces

class CouchbaseSpringRepositoryTest extends AbstractCouchbaseTest {
  private static final Closure<Doc> FIND
  static {
    // This method is different in Spring Data 2+
    try {
      CrudRepository.getMethod("findOne", Serializable)
      FIND = { DocRepository repo, String id ->
        repo.findOne(id)
      }
    } catch (NoSuchMethodException e) {
      FIND = { DocRepository repo, String id ->
        repo.findById(id).get()
      }
    }
  }
  @Shared
  ApplicationContext applicationContext
  @Shared
  DocRepository repo

  def setupSpec() {
    CouchbaseConfig.setEnvironment(environment)

    // Close all buckets and disconnect
    cluster.disconnect()

    applicationContext = new AnnotationConfigApplicationContext(CouchbaseConfig)
    repo = applicationContext.getBean(DocRepository)
  }

  def setup() {
    repo.deleteAll()
    TEST_WRITER.waitForTraces(1) // There might be more if there were documents to delete
    TEST_WRITER.clear()
  }

  def "test empty repo"() {
    when:
    def result = repo.findAll()

    then:
    !result.iterator().hasNext()

    and:
    assertTraces(TEST_WRITER, 1) {
      trace(0, 1) {
        span(0) {
          serviceName "couchbase"
          resourceName "Bucket.query(${bucketCouchbase.name()})"
          operationName "couchbase.call"
          errored false
          parent()
          tags {
            "bucket" bucketCouchbase.name()
            defaultTags()
          }
        }
      }
    }

    where:
    indexName = "test-index"
  }

  def "test CRUD"() {
    when:
    def doc = new Doc()

    then: // CREATE
    repo.save(doc) == doc

    and:
    assertTraces(TEST_WRITER, 1) {
      trace(0, 1) {
        span(0) {
          serviceName "couchbase"
          resourceName "Bucket.upsert(${bucketCouchbase.name()})"
          operationName "couchbase.call"
          errored false
          parent()
          tags {
            "bucket" bucketCouchbase.name()
            defaultTags()
          }
        }
      }
    }
    TEST_WRITER.clear()

    and: // RETRIEVE
    FIND(repo, "1") == doc

    and:
    assertTraces(TEST_WRITER, 1) {
      trace(0, 1) {
        span(0) {
          serviceName "couchbase"
          resourceName "Bucket.get(${bucketCouchbase.name()})"
          operationName "couchbase.call"
          errored false
          parent()
          tags {
            "bucket" bucketCouchbase.name()
            defaultTags()
          }
        }
      }
    }
    TEST_WRITER.clear()

    when:
    doc.data = "other data"

    then: // UPDATE
    repo.save(doc) == doc
    FIND(repo, "1") == doc
    // findAll doesn't seem to be working.
//    repo.findAll().asList() == [doc]

    and:
    assertTraces(TEST_WRITER, 2) {
      trace(0, 1) {
        span(0) {
          serviceName "couchbase"
          resourceName "Bucket.upsert(${bucketCouchbase.name()})"
          operationName "couchbase.call"
          errored false
          parent()
          tags {
            "bucket" bucketCouchbase.name()
            defaultTags()
          }
        }
      }
      trace(1, 1) {
        span(0) {
          serviceName "couchbase"
          resourceName "Bucket.get(${bucketCouchbase.name()})"
          operationName "couchbase.call"
          errored false
          parent()
          tags {
            "bucket" bucketCouchbase.name()
            defaultTags()
          }
        }
      }
    }
    TEST_WRITER.clear()

    when: // DELETE
    repo.delete("1")

    then:
    !repo.findAll().iterator().hasNext()

    and:
    assertTraces(TEST_WRITER, 2) {
      trace(0, 1) {
        span(0) {
          serviceName "couchbase"
          resourceName "Bucket.remove(${bucketCouchbase.name()})"
          operationName "couchbase.call"
          errored false
          parent()
          tags {
            "bucket" bucketCouchbase.name()
            defaultTags()
          }
        }
      }
      trace(1, 1) {
        span(0) {
          serviceName "couchbase"
          resourceName "Bucket.query(${bucketCouchbase.name()})"
          operationName "couchbase.call"
          errored false
          parent()
          tags {
            "bucket" bucketCouchbase.name()
            defaultTags()
          }
        }
      }
    }
  }
}
