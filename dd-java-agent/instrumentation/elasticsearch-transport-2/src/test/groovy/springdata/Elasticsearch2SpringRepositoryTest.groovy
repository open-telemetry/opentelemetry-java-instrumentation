package springdata

import datadog.trace.agent.test.AgentTestRunner
import io.opentracing.tag.Tags
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import spock.lang.Shared

import static datadog.trace.agent.test.ListWriterAssert.assertTraces

class Elasticsearch2SpringRepositoryTest extends AgentTestRunner {
  static {
    System.setProperty("dd.integration.elasticsearch.enabled", "true")
  }

  @Shared
  ApplicationContext applicationContext = new AnnotationConfigApplicationContext(Config)

  @Shared
  DocRepository repo = applicationContext.getBean(DocRepository)

  def setup() {
    repo.deleteAll()
    TEST_WRITER.waitForTraces(4)
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
          serviceName "elasticsearch"
          resourceName "SearchAction"
          operationName "elasticsearch.query"
          spanType null
          errored false
          tags {
            "$Tags.COMPONENT.key" "elasticsearch-java"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "elasticsearch.action" "SearchAction"
            "elasticsearch.request" "SearchRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.request.search.types" "doc"
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

    then:
    repo.index(doc) == doc

    and:
    assertTraces(TEST_WRITER, 3) {
      trace(0, 1) {
        span(0) {
          resourceName "PutMappingAction"
          operationName "elasticsearch.query"
          tags {
            "$Tags.COMPONENT.key" "elasticsearch-java"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "elasticsearch.action" "PutMappingAction"
            "elasticsearch.request" "PutMappingRequest"
            "elasticsearch.request.indices" indexName
            defaultTags()
          }
        }
      }
      trace(1, 1) {
        span(0) {
          resourceName "IndexAction"
          operationName "elasticsearch.query"
          tags {
            "$Tags.COMPONENT.key" "elasticsearch-java"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$Tags.PEER_HOSTNAME.key" "local"
            "$Tags.PEER_HOST_IPV4.key" "0.0.0.0"
            "$Tags.PEER_PORT.key" 0
            "elasticsearch.action" "IndexAction"
            "elasticsearch.request" "IndexRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.request.write.type" "doc"
            defaultTags()
          }
        }
      }
      trace(2, 1) {
        span(0) {
          resourceName "RefreshAction"
          operationName "elasticsearch.query"
          tags {
            "$Tags.COMPONENT.key" "elasticsearch-java"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "elasticsearch.action" "RefreshAction"
            "elasticsearch.request" "RefreshRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.shard.broadcast.failed" 0
            "elasticsearch.shard.broadcast.successful" 5
            "elasticsearch.shard.broadcast.total" 10
            defaultTags()
          }
        }
      }
    }
    TEST_WRITER.clear()

    and:
    repo.findOne("1") == doc

    and:
    assertTraces(TEST_WRITER, 1) {
      trace(0, 1) {
        span(0) {
          serviceName "elasticsearch"
          resourceName "GetAction"
          operationName "elasticsearch.query"
          tags {
            "$Tags.COMPONENT.key" "elasticsearch-java"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$Tags.PEER_HOSTNAME.key" "local"
            "$Tags.PEER_HOST_IPV4.key" "0.0.0.0"
            "$Tags.PEER_PORT.key" 0
            "elasticsearch.action" "GetAction"
            "elasticsearch.request" "GetRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.type" "doc"
            "elasticsearch.id" "1"
            "elasticsearch.version" 1
            defaultTags()
          }
        }
      }
    }
    TEST_WRITER.clear()

    when:
    doc.data == "other data"

    then:
    repo.index(doc) == doc
    repo.findOne("1") == doc

    and:
    assertTraces(TEST_WRITER, 3) {
      trace(0, 1) {
        span(0) {
          resourceName "IndexAction"
          operationName "elasticsearch.query"
          tags {
            "$Tags.COMPONENT.key" "elasticsearch-java"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$Tags.PEER_HOSTNAME.key" "local"
            "$Tags.PEER_HOST_IPV4.key" "0.0.0.0"
            "$Tags.PEER_PORT.key" 0
            "elasticsearch.action" "IndexAction"
            "elasticsearch.request" "IndexRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.request.write.type" "doc"
            defaultTags()
          }
        }
      }
      trace(1, 1) {
        span(0) {
          resourceName "RefreshAction"
          operationName "elasticsearch.query"
          tags {
            "$Tags.COMPONENT.key" "elasticsearch-java"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "elasticsearch.action" "RefreshAction"
            "elasticsearch.request" "RefreshRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.shard.broadcast.failed" 0
            "elasticsearch.shard.broadcast.successful" 5
            "elasticsearch.shard.broadcast.total" 10
            defaultTags()
          }
        }
      }
      trace(2, 1) {
        span(0) {
          serviceName "elasticsearch"
          resourceName "GetAction"
          operationName "elasticsearch.query"
          tags {
            "$Tags.COMPONENT.key" "elasticsearch-java"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$Tags.PEER_HOSTNAME.key" "local"
            "$Tags.PEER_HOST_IPV4.key" "0.0.0.0"
            "$Tags.PEER_PORT.key" 0
            "elasticsearch.action" "GetAction"
            "elasticsearch.request" "GetRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.type" "doc"
            "elasticsearch.id" "1"
            "elasticsearch.version" 2
            defaultTags()
          }
        }
      }
    }
    TEST_WRITER.clear()

    when:
    repo.delete("1")

    then:
    !repo.findAll().iterator().hasNext()

    and:
    assertTraces(TEST_WRITER, 3) {
      trace(0, 1) {
        span(0) {
          resourceName "DeleteAction"
          operationName "elasticsearch.query"
          tags {
            "$Tags.COMPONENT.key" "elasticsearch-java"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$Tags.PEER_HOSTNAME.key" "local"
            "$Tags.PEER_HOST_IPV4.key" "0.0.0.0"
            "$Tags.PEER_PORT.key" 0
            "elasticsearch.action" "DeleteAction"
            "elasticsearch.request" "DeleteRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.request.write.type" "doc"
            defaultTags()
          }
        }
      }
      trace(1, 1) {
        span(0) {
          resourceName "RefreshAction"
          operationName "elasticsearch.query"
          tags {
            "$Tags.COMPONENT.key" "elasticsearch-java"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "elasticsearch.action" "RefreshAction"
            "elasticsearch.request" "RefreshRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.shard.broadcast.failed" 0
            "elasticsearch.shard.broadcast.successful" 5
            "elasticsearch.shard.broadcast.total" 10
            defaultTags()
          }
        }
      }
      trace(2, 1) {
        span(0) {
          serviceName "elasticsearch"
          resourceName "SearchAction"
          operationName "elasticsearch.query"
          tags {
            "$Tags.COMPONENT.key" "elasticsearch-java"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "elasticsearch.action" "SearchAction"
            "elasticsearch.request" "SearchRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.request.search.types" "doc"
            defaultTags()
          }
        }
      }
    }

    where:
    indexName = "test-index"
  }
}
