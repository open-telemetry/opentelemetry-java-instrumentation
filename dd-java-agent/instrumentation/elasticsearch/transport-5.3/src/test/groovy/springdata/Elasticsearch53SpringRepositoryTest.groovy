package springdata

import com.anotherchrisberry.spock.extensions.retry.RetryOnFailure
import datadog.opentracing.DDSpan
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.api.DDSpanTypes
import datadog.trace.bootstrap.instrumentation.api.Tags
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import spock.lang.Shared

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

import static datadog.trace.agent.test.utils.TraceUtils.runUnderTrace

@RetryOnFailure(times = 3, delaySeconds = 1)
class Elasticsearch53SpringRepositoryTest extends AgentTestRunner {
  // Setting up appContext & repo with @Shared doesn't allow
  // spring-data instrumentation to applied.
  // To change the timing without adding ugly checks everywhere -
  // use a dynamic proxy.  There's probably a more "groovy" way to do this.

  @Shared
  DocRepository repo = Proxy.newProxyInstance(
    getClass().getClassLoader(),
    [DocRepository] as Class[],
    new LazyProxyInvoker())

  static class LazyProxyInvoker implements InvocationHandler {
    def repo

    DocRepository getOrCreateRepository() {
      if (repo != null) {
        return repo
      }

      def applicationContext = new AnnotationConfigApplicationContext(Config)
      repo = applicationContext.getBean(DocRepository)

      return repo
    }

    @Override
    Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      return method.invoke(getOrCreateRepository(), args)
    }
  }

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
    waitForTracesAndSortSpans(1)
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          operationName "repository.operation"
          resourceName "CrudRepository.findAll"
          tags {
            "$Tags.COMPONENT" "spring-data"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            defaultTags()
          }
        }

        span(1) {
          serviceName "elasticsearch"
          resourceName "SearchAction"
          operationName "elasticsearch.query"
          spanType DDSpanTypes.ELASTICSEARCH
          errored false
          childOf(span(0))

          tags {
            "$Tags.COMPONENT" "elasticsearch-java"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "$Tags.DB_TYPE" "elasticsearch"
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
    waitForTracesAndSortSpans(2)
    // need to normalize trace ordering since they are finished by different threads
    if (TEST_WRITER[1][0].resourceName == "PutMappingAction") {
      def tmp = TEST_WRITER[1]
      TEST_WRITER[1] = TEST_WRITER[0]
      TEST_WRITER[0] = tmp
    }
    assertTraces(2) {
      trace(0, 1) {
        span(0) {
          serviceName "elasticsearch"
          resourceName "PutMappingAction"
          operationName "elasticsearch.query"
          spanType DDSpanTypes.ELASTICSEARCH
          tags {
            "$Tags.COMPONENT" "elasticsearch-java"
            "$Tags.DB_TYPE" "elasticsearch"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "elasticsearch.action" "PutMappingAction"
            "elasticsearch.request" "PutMappingRequest"
            defaultTags()
          }
        }
      }
      trace(1, 3) {
        span(0) {
          resourceName "ElasticsearchRepository.index"
          operationName "repository.operation"
          tags {
            "$Tags.COMPONENT" "spring-data"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            defaultTags()
          }
        }

        span(1) {
          resourceName "RefreshAction"
          operationName "elasticsearch.query"
          spanType DDSpanTypes.ELASTICSEARCH
          childOf(span(0))
          tags {
            "$Tags.COMPONENT" "elasticsearch-java"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "$Tags.DB_TYPE" "elasticsearch"
            "elasticsearch.action" "RefreshAction"
            "elasticsearch.request" "RefreshRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.shard.broadcast.failed" 0
            "elasticsearch.shard.broadcast.successful" 5
            "elasticsearch.shard.broadcast.total" 10
            defaultTags()
          }
        }

        span(2) {
          resourceName "IndexAction"
          operationName "elasticsearch.query"
          spanType DDSpanTypes.ELASTICSEARCH
          childOf(span(0))
          tags {
            "$Tags.COMPONENT" "elasticsearch-java"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "$Tags.DB_TYPE" "elasticsearch"
            "elasticsearch.action" "IndexAction"
            "elasticsearch.request" "IndexRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.request.write.type" "doc"
            "elasticsearch.request.write.version"(-3)
            "elasticsearch.response.status" 201
            "elasticsearch.shard.replication.failed" 0
            "elasticsearch.shard.replication.successful" 1
            "elasticsearch.shard.replication.total" 2
            defaultTags()
          }
        }
      }
    }
    TEST_WRITER.clear()

    and:
    repo.findById("1").get() == doc

    and:
    waitForTracesAndSortSpans(1)
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          resourceName "CrudRepository.findById"
          operationName "repository.operation"
          tags {
            "$Tags.COMPONENT" "spring-data"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            defaultTags()
          }
        }

        span(1) {
          serviceName "elasticsearch"
          resourceName "GetAction"
          operationName "elasticsearch.query"
          spanType DDSpanTypes.ELASTICSEARCH
          childOf(span(0))
          tags {
            "$Tags.COMPONENT" "elasticsearch-java"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "$Tags.DB_TYPE" "elasticsearch"
            "elasticsearch.action" "GetAction"
            "elasticsearch.request" "GetRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.type" "doc"
            "elasticsearch.id" "1"
            "elasticsearch.version" Number
            defaultTags()
          }
        }
      }
    }
    TEST_WRITER.clear()

    when:
    doc.data = "other data"

    then:
    repo.index(doc) == doc
    repo.findById("1").get() == doc

    and:
    waitForTracesAndSortSpans(2)
    assertTraces(2) {
      trace(0, 3) {
        span(0) {
          resourceName "ElasticsearchRepository.index"
          operationName "repository.operation"
          tags {
            "$Tags.COMPONENT" "spring-data"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            defaultTags()
          }
        }
        span(1) {
          resourceName "RefreshAction"
          operationName "elasticsearch.query"
          spanType DDSpanTypes.ELASTICSEARCH
          childOf(span(0))
          tags {
            "$Tags.COMPONENT" "elasticsearch-java"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "$Tags.DB_TYPE" "elasticsearch"
            "elasticsearch.action" "RefreshAction"
            "elasticsearch.request" "RefreshRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.shard.broadcast.failed" 0
            "elasticsearch.shard.broadcast.successful" 5
            "elasticsearch.shard.broadcast.total" 10
            defaultTags()
          }
        }
        span(2) {
          resourceName "IndexAction"
          operationName "elasticsearch.query"
          spanType DDSpanTypes.ELASTICSEARCH
          childOf(span(0))
          tags {
            "$Tags.COMPONENT" "elasticsearch-java"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "$Tags.DB_TYPE" "elasticsearch"
            "elasticsearch.action" "IndexAction"
            "elasticsearch.request" "IndexRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.request.write.type" "doc"
            "elasticsearch.request.write.version"(-3)
            "elasticsearch.response.status" 200
            "elasticsearch.shard.replication.failed" 0
            "elasticsearch.shard.replication.successful" 1
            "elasticsearch.shard.replication.total" 2
            defaultTags()
          }
        }
      }
      trace(1, 2) {
        span(0) {
          resourceName "CrudRepository.findById"
          operationName "repository.operation"
          tags {
            "$Tags.COMPONENT" "spring-data"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            defaultTags()
          }
        }

        span(1) {
          serviceName "elasticsearch"
          resourceName "GetAction"
          operationName "elasticsearch.query"
          spanType DDSpanTypes.ELASTICSEARCH
          childOf(span(0))
          tags {
            "$Tags.COMPONENT" "elasticsearch-java"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "$Tags.DB_TYPE" "elasticsearch"
            "elasticsearch.action" "GetAction"
            "elasticsearch.request" "GetRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.type" "doc"
            "elasticsearch.id" "1"
            "elasticsearch.version" Number
            defaultTags()
          }
        }
      }
    }
    TEST_WRITER.clear()

    when:
    repo.deleteById("1")

    then:
    !repo.findAll().iterator().hasNext()

    and:
    waitForTracesAndSortSpans(2)
    assertTraces(2) {
      trace(0, 3) {
        span(0) {
          resourceName "CrudRepository.deleteById"
          operationName "repository.operation"
          tags {
            "$Tags.COMPONENT" "spring-data"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            defaultTags()
          }
        }

        span(1) {
          resourceName "RefreshAction"
          operationName "elasticsearch.query"
          spanType DDSpanTypes.ELASTICSEARCH
          childOf(span(0))
          tags {
            "$Tags.COMPONENT" "elasticsearch-java"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "$Tags.DB_TYPE" "elasticsearch"
            "elasticsearch.action" "RefreshAction"
            "elasticsearch.request" "RefreshRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.shard.broadcast.failed" 0
            "elasticsearch.shard.broadcast.successful" 5
            "elasticsearch.shard.broadcast.total" 10
            defaultTags()
          }
        }
        span(2) {
          resourceName "DeleteAction"
          operationName "elasticsearch.query"
          spanType DDSpanTypes.ELASTICSEARCH
          childOf(span(0))
          tags {
            "$Tags.COMPONENT" "elasticsearch-java"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "$Tags.DB_TYPE" "elasticsearch"
            "elasticsearch.action" "DeleteAction"
            "elasticsearch.request" "DeleteRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.request.write.type" "doc"
            "elasticsearch.request.write.version"(-3)
            "elasticsearch.shard.replication.failed" 0
            "elasticsearch.shard.replication.successful" 1
            "elasticsearch.shard.replication.total" 2
            defaultTags()
          }
        }
      }

      trace(1, 2) {
        span(0) {
          resourceName "CrudRepository.findAll"
          operationName "repository.operation"
          tags {
            "$Tags.COMPONENT" "spring-data"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            defaultTags()
          }
        }

        span(1) {
          serviceName "elasticsearch"
          resourceName "SearchAction"
          operationName "elasticsearch.query"
          spanType DDSpanTypes.ELASTICSEARCH
          childOf(span(0))
          tags {
            "$Tags.COMPONENT" "elasticsearch-java"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "$Tags.DB_TYPE" "elasticsearch"
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

  def waitForTracesAndSortSpans(int number) {
    TEST_WRITER.waitForTraces(number)
    for (List<DDSpan> trace : TEST_WRITER) {
      // need to normalize span ordering since they are finished by different threads
      if (trace.size() > 1 && trace[1].operationName == "repository.operation") {
        def tmp = trace[1]
        trace[1] = trace[0]
        trace[0] = tmp
      }
    }
    return true
  }
}
