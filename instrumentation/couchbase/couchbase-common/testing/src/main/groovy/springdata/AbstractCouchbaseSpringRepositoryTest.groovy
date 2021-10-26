/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package springdata


import com.couchbase.client.java.Cluster
import com.couchbase.client.java.CouchbaseCluster
import com.couchbase.client.java.env.CouchbaseEnvironment
import com.couchbase.client.java.view.DefaultView
import com.couchbase.client.java.view.DesignDocument
import io.opentelemetry.api.trace.SpanKind
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.data.repository.CrudRepository
import spock.lang.Shared
import spock.lang.Unroll
import util.AbstractCouchbaseTest

@Unroll
abstract class AbstractCouchbaseSpringRepositoryTest extends AbstractCouchbaseTest {
  static final Closure<Doc> FIND
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
  ConfigurableApplicationContext applicationContext
  @Shared
  DocRepository repo

  def setupSpec() {
    CouchbaseEnvironment environment = envBuilder(bucketCouchbase).build()
    Cluster couchbaseCluster = CouchbaseCluster.create(environment, Arrays.asList("127.0.0.1"))

    // Create view for SpringRepository's findAll()
    couchbaseCluster.openBucket(bucketCouchbase.name(), bucketCouchbase.password()).bucketManager()
      .insertDesignDocument(
        DesignDocument.create("doc", Collections.singletonList(DefaultView.create("all",
          '''
          function (doc, meta) {
             if (doc._class == "springdata.Doc") {
               emit(meta.id, null);
             }
          }
        '''.stripIndent()
        )))
      )
    CouchbaseConfig.setEnvironment(environment)
    CouchbaseConfig.setBucketSettings(bucketCouchbase)

    // Close all buckets and disconnect
    couchbaseCluster.disconnect()

    applicationContext = new AnnotationConfigApplicationContext(CouchbaseConfig)
    repo = applicationContext.getBean(DocRepository)
  }

  def cleanupSpec() {
    applicationContext.close()
  }

  def "test empty repo"() {
    when:
    def result = repo.findAll()

    then:
    !result.iterator().hasNext()

    and:
    assertTraces(1) {
      trace(0, 1) {
        def dbName = bucketCouchbase.name()
        assertCouchbaseCall(it, 0, dbName, null, dbName, ~/^ViewQuery\(doc\/all\).*/, { it == null })
      }
    }
  }

  def "test save"() {
    setup:
    def doc = new Doc()

    when:
    def result = repo.save(doc)

    then:
    result == doc
    assertTraces(1) {
      trace(0, 1) {
        assertCouchbaseCall(it, 0, "Bucket.upsert", null, bucketCouchbase.name())
      }
    }

    cleanup:
    clearExportedData()
    repo.deleteAll()
    ignoreTracesAndClear(2)
  }

  def "test save and retrieve"() {
    setup:
    def doc = new Doc()
    def result

    when:
    runWithSpan("someTrace") {
      repo.save(doc)
      result = FIND(repo, "1")
    }

    then: // RETRIEVE
    result == doc
    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          name "someTrace"
          kind SpanKind.INTERNAL
          hasNoParent()
        }
        assertCouchbaseCall(it, 1, "Bucket.upsert", span(0), bucketCouchbase.name())
        assertCouchbaseCall(it, 2, "Bucket.get", span(0), bucketCouchbase.name())
      }
    }

    cleanup:
    clearExportedData()
    repo.deleteAll()
    ignoreTracesAndClear(2)
  }

  def "test save and update"() {
    setup:
    def doc = new Doc()

    when:
    runWithSpan("someTrace") {
      repo.save(doc)
      doc.data = "other data"
      repo.save(doc)
    }


    then:
    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          name "someTrace"
          kind SpanKind.INTERNAL
          hasNoParent()
        }
        assertCouchbaseCall(it, 1, "Bucket.upsert", span(0), bucketCouchbase.name())
        assertCouchbaseCall(it, 2, "Bucket.upsert", span(0), bucketCouchbase.name())
      }
    }

    cleanup:
    clearExportedData()
    repo.deleteAll()
    ignoreTracesAndClear(2)
  }

  def "save and delete"() {
    setup:
    def doc = new Doc()
    def result

    when: // DELETE
    runWithSpan("someTrace") {
      repo.save(doc)
      repo.delete("1")
      result = repo.findAll().iterator().hasNext()
    }

    then:
    assert !result
    assertTraces(1) {
      trace(0, 4) {
        span(0) {
          name "someTrace"
          kind SpanKind.INTERNAL
          hasNoParent()
        }

        def dbName = bucketCouchbase.name()
        assertCouchbaseCall(it, 1, "Bucket.upsert", span(0), dbName)
        assertCouchbaseCall(it, 2, "Bucket.remove", span(0), dbName)
        assertCouchbaseCall(it, 3, dbName, span(0), dbName, ~/^ViewQuery\(doc\/all\).*/, { it == null })
      }
    }
  }
}
