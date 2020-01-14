package springdata

import com.anotherchrisberry.spock.extensions.retry.RetryOnFailure
import io.opentelemetry.auto.api.MoreTags
import io.opentelemetry.auto.api.SpanTypes
import io.opentelemetry.auto.instrumentation.api.Tags
import io.opentelemetry.auto.test.AgentTestRunner
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import spock.lang.Shared

import static io.opentelemetry.auto.test.utils.TraceUtils.runUnderTrace

@RetryOnFailure(times = 3, delaySeconds = 1)
class Elasticsearch2SpringRepositoryTest extends AgentTestRunner {
  @Shared
  ApplicationContext applicationContext = new AnnotationConfigApplicationContext(Config)

  @Shared
  DocRepository repo = applicationContext.getBean(DocRepository)

  def setup() {
    TEST_WRITER.clear()
    runUnderTrace("delete") {
      repo.deleteAll()
    }
    TEST_WRITER.waitForTraces(1)
    TEST_WRITER.clear()
  }

  def "test empty repo"() {
    when:
    def result = repo.findAll()

    then:
    !result.iterator().hasNext()

    and:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName "elasticsearch.query"
          errored false
          tags {
            "$MoreTags.SERVICE_NAME" "elasticsearch"
            "$MoreTags.RESOURCE_NAME" "SearchAction"
            "$MoreTags.SPAN_TYPE" SpanTypes.ELASTICSEARCH
            "$Tags.COMPONENT" "elasticsearch-java"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "$Tags.DB_TYPE" "elasticsearch"
            "elasticsearch.action" "SearchAction"
            "elasticsearch.request" "SearchRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.request.search.types" "doc"
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
    assertTraces(3) {
      sortTraces {
        // IndexAction and PutMappingAction run in separate threads and so their order is not always the same
        if (traces[0][0].attributes[MoreTags.RESOURCE_NAME].stringValue == "IndexAction") {
          def tmp = traces[0]
          traces[0] = traces[1]
          traces[1] = tmp
        }
      }
      trace(0, 1) {
        span(0) {
          operationName "elasticsearch.query"
          tags {
            "$MoreTags.SERVICE_NAME" "elasticsearch"
            "$MoreTags.RESOURCE_NAME" "PutMappingAction"
            "$MoreTags.SPAN_TYPE" SpanTypes.ELASTICSEARCH
            "$Tags.COMPONENT" "elasticsearch-java"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "$Tags.DB_TYPE" "elasticsearch"
            "elasticsearch.action" "PutMappingAction"
            "elasticsearch.request" "PutMappingRequest"
            "elasticsearch.request.indices" indexName
          }
        }
      }
      trace(1, 1) {
        span(0) {
          operationName "elasticsearch.query"
          tags {
            "$MoreTags.SERVICE_NAME" "elasticsearch"
            "$MoreTags.RESOURCE_NAME" "IndexAction"
            "$MoreTags.SPAN_TYPE" SpanTypes.ELASTICSEARCH
            "$Tags.COMPONENT" "elasticsearch-java"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "$Tags.DB_TYPE" "elasticsearch"
            "elasticsearch.action" "IndexAction"
            "elasticsearch.request" "IndexRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.request.write.type" "doc"
          }
        }
      }
      trace(2, 1) {
        span(0) {
          operationName "elasticsearch.query"
          tags {
            "$MoreTags.SERVICE_NAME" "elasticsearch"
            "$MoreTags.RESOURCE_NAME" "RefreshAction"
            "$MoreTags.SPAN_TYPE" SpanTypes.ELASTICSEARCH
            "$Tags.COMPONENT" "elasticsearch-java"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "$Tags.DB_TYPE" "elasticsearch"
            "elasticsearch.action" "RefreshAction"
            "elasticsearch.request" "RefreshRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.shard.broadcast.failed" 0
            "elasticsearch.shard.broadcast.successful" 5
            "elasticsearch.shard.broadcast.total" 10
          }
        }
      }
    }
    TEST_WRITER.clear()

    and:
    repo.findOne("1") == doc

    and:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName "elasticsearch.query"
          tags {
            "$MoreTags.SERVICE_NAME" "elasticsearch"
            "$MoreTags.RESOURCE_NAME" "GetAction"
            "$MoreTags.SPAN_TYPE" SpanTypes.ELASTICSEARCH
            "$Tags.COMPONENT" "elasticsearch-java"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "$Tags.DB_TYPE" "elasticsearch"
            "elasticsearch.action" "GetAction"
            "elasticsearch.request" "GetRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.type" "doc"
            "elasticsearch.id" "1"
            "elasticsearch.version" Number
          }
        }
      }
    }
    TEST_WRITER.clear()

    when:
    doc.data = "other data"

    then:
    repo.index(doc) == doc
    repo.findOne("1") == doc

    and:
    assertTraces(3) {
      trace(0, 1) {
        span(0) {
          operationName "elasticsearch.query"
          tags {
            "$MoreTags.SERVICE_NAME" "elasticsearch"
            "$MoreTags.RESOURCE_NAME" "IndexAction"
            "$MoreTags.SPAN_TYPE" SpanTypes.ELASTICSEARCH
            "$Tags.COMPONENT" "elasticsearch-java"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "$Tags.DB_TYPE" "elasticsearch"
            "elasticsearch.action" "IndexAction"
            "elasticsearch.request" "IndexRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.request.write.type" "doc"
          }
        }
      }
      trace(1, 1) {
        span(0) {
          operationName "elasticsearch.query"
          tags {
            "$MoreTags.SERVICE_NAME" "elasticsearch"
            "$MoreTags.RESOURCE_NAME" "RefreshAction"
            "$MoreTags.SPAN_TYPE" SpanTypes.ELASTICSEARCH
            "$Tags.COMPONENT" "elasticsearch-java"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "$Tags.DB_TYPE" "elasticsearch"
            "elasticsearch.action" "RefreshAction"
            "elasticsearch.request" "RefreshRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.shard.broadcast.failed" 0
            "elasticsearch.shard.broadcast.successful" 5
            "elasticsearch.shard.broadcast.total" 10
          }
        }
      }
      trace(2, 1) {
        span(0) {
          operationName "elasticsearch.query"
          tags {
            "$MoreTags.SERVICE_NAME" "elasticsearch"
            "$MoreTags.RESOURCE_NAME" "GetAction"
            "$MoreTags.SPAN_TYPE" SpanTypes.ELASTICSEARCH
            "$Tags.COMPONENT" "elasticsearch-java"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "$Tags.DB_TYPE" "elasticsearch"
            "elasticsearch.action" "GetAction"
            "elasticsearch.request" "GetRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.type" "doc"
            "elasticsearch.id" "1"
            "elasticsearch.version" Number
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
    assertTraces(3) {
      trace(0, 1) {
        span(0) {
          operationName "elasticsearch.query"
          tags {
            "$MoreTags.SERVICE_NAME" "elasticsearch"
            "$MoreTags.RESOURCE_NAME" "DeleteAction"
            "$MoreTags.SPAN_TYPE" SpanTypes.ELASTICSEARCH
            "$Tags.COMPONENT" "elasticsearch-java"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "$Tags.DB_TYPE" "elasticsearch"
            "elasticsearch.action" "DeleteAction"
            "elasticsearch.request" "DeleteRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.request.write.type" "doc"
          }
        }
      }
      trace(1, 1) {
        span(0) {
          operationName "elasticsearch.query"
          tags {
            "$MoreTags.SERVICE_NAME" "elasticsearch"
            "$MoreTags.RESOURCE_NAME" "RefreshAction"
            "$MoreTags.SPAN_TYPE" SpanTypes.ELASTICSEARCH
            "$Tags.COMPONENT" "elasticsearch-java"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "$Tags.DB_TYPE" "elasticsearch"
            "elasticsearch.action" "RefreshAction"
            "elasticsearch.request" "RefreshRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.shard.broadcast.failed" 0
            "elasticsearch.shard.broadcast.successful" 5
            "elasticsearch.shard.broadcast.total" 10
          }
        }
      }
      trace(2, 1) {
        span(0) {
          operationName "elasticsearch.query"
          tags {
            "$MoreTags.SERVICE_NAME" "elasticsearch"
            "$MoreTags.RESOURCE_NAME" "SearchAction"
            "$MoreTags.SPAN_TYPE" SpanTypes.ELASTICSEARCH
            "$Tags.COMPONENT" "elasticsearch-java"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "$Tags.DB_TYPE" "elasticsearch"
            "elasticsearch.action" "SearchAction"
            "elasticsearch.request" "SearchRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.request.search.types" "doc"
          }
        }
      }
    }

    where:
    indexName = "test-index"
  }
}
