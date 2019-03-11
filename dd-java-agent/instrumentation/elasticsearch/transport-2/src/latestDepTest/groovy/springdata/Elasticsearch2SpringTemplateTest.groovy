package springdata

import com.anotherchrisberry.spock.extensions.retry.RetryOnFailure
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.utils.PortUtils
import datadog.trace.api.DDSpanTypes
import io.opentracing.tag.Tags
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.common.io.FileSystemUtils
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.index.IndexNotFoundException
import org.elasticsearch.node.Node
import org.elasticsearch.node.NodeBuilder
import org.elasticsearch.search.aggregations.bucket.nested.InternalNested
import org.elasticsearch.search.aggregations.bucket.terms.Terms
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate
import org.springframework.data.elasticsearch.core.ResultsExtractor
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder
import spock.lang.Shared

import java.util.concurrent.atomic.AtomicLong

@RetryOnFailure(times = 3, delaySeconds = 1)
class Elasticsearch2SpringTemplateTest extends AgentTestRunner {
  public static final long TIMEOUT = 10000; // 10 seconds

  @Shared
  int httpPort
  @Shared
  int tcpPort
  @Shared
  Node testNode
  @Shared
  File esWorkingDir

  @Shared
  ElasticsearchTemplate template

  def setupSpec() {
    httpPort = PortUtils.randomOpenPort()
    tcpPort = PortUtils.randomOpenPort()

    esWorkingDir = File.createTempDir("test-es-working-dir-", "")
    esWorkingDir.deleteOnExit()
    println "ES work dir: $esWorkingDir"

    def settings = Settings.builder()
      .put("path.home", esWorkingDir.path)
    // Since we use listeners to close spans this should make our span closing deterministic which is good for tests
      .put("threadpool.listener.size", 1)
      .put("http.port", httpPort)
      .put("transport.tcp.port", tcpPort)
      .build()
    testNode = NodeBuilder.newInstance().local(true).clusterName("test-cluster").settings(settings).build()
    testNode.start()

    template = new ElasticsearchTemplate(testNode.client())
  }

  def cleanupSpec() {
    testNode?.close()
    if (esWorkingDir != null) {
      FileSystemUtils.deleteSubDirectories(esWorkingDir.toPath())
      esWorkingDir.delete()
    }
  }

  def "test elasticsearch error"() {
    when:
    template.refresh(indexName)

    then:
    thrown IndexNotFoundException

    and:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          serviceName "elasticsearch"
          resourceName "RefreshAction"
          operationName "elasticsearch.query"
          spanType DDSpanTypes.ELASTICSEARCH
          errored true
          tags {
            "$Tags.COMPONENT.key" "elasticsearch-java"
            "$Tags.DB_TYPE.key" "elasticsearch"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "elasticsearch.action" "RefreshAction"
            "elasticsearch.request" "RefreshRequest"
            "elasticsearch.request.indices" indexName
            errorTags IndexNotFoundException, "no such index"
            defaultTags()
          }
        }
      }
    }

    where:
    indexName = "invalid-index"
  }

  def "test elasticsearch get"() {
    expect:
    template.createIndex(indexName)
    TEST_WRITER.waitForTraces(1)
    template.getClient().admin().cluster().prepareHealth().setWaitForYellowStatus().execute().actionGet(TIMEOUT)
    TEST_WRITER.waitForTraces(2)

    when:
    NativeSearchQuery query = new NativeSearchQueryBuilder()
      .withIndices(indexName)
      .withTypes(indexType)
      .withIds([id])
      .build()

    then:
    template.queryForIds(query) == []

    when:
    def result = template.index(IndexQueryBuilder.newInstance()
      .withObject(new Doc())
      .withIndexName(indexName)
      .withType(indexType)
      .withId(id)
      .build())
    template.refresh(Doc)

    then:
    result == id
    template.queryForList(query, Doc) == [new Doc()]

    and:
    // IndexAction and PutMappingAction run in separate threads and order in which
    // these spans are closed is not defined. So we force the order if it is wrong.
    if (TEST_WRITER[3][0].resourceName == "IndexAction") {
      def tmp = TEST_WRITER[3]
      TEST_WRITER[3] = TEST_WRITER[4]
      TEST_WRITER[4] = tmp
    }
    assertTraces(7) {
      trace(0, 1) {
        span(0) {
          serviceName "elasticsearch"
          resourceName "CreateIndexAction"
          operationName "elasticsearch.query"
          spanType DDSpanTypes.ELASTICSEARCH
          tags {
            "$Tags.COMPONENT.key" "elasticsearch-java"
            "$Tags.DB_TYPE.key" "elasticsearch"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "elasticsearch.action" "CreateIndexAction"
            "elasticsearch.request" "CreateIndexRequest"
            "elasticsearch.request.indices" indexName
            defaultTags()
          }
        }
      }
      trace(1, 1) {
        span(0) {
          serviceName "elasticsearch"
          resourceName "ClusterHealthAction"
          operationName "elasticsearch.query"
          spanType DDSpanTypes.ELASTICSEARCH
          tags {
            "$Tags.COMPONENT.key" "elasticsearch-java"
            "$Tags.DB_TYPE.key" "elasticsearch"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "elasticsearch.action" "ClusterHealthAction"
            "elasticsearch.request" "ClusterHealthRequest"
            defaultTags()
          }
        }
      }
      trace(2, 1) {
        span(0) {
          serviceName "elasticsearch"
          resourceName "SearchAction"
          operationName "elasticsearch.query"
          spanType DDSpanTypes.ELASTICSEARCH
          tags {
            "$Tags.COMPONENT.key" "elasticsearch-java"
            "$Tags.DB_TYPE.key" "elasticsearch"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "elasticsearch.action" "SearchAction"
            "elasticsearch.request" "SearchRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.request.search.types" indexType
            defaultTags()
          }
        }
      }
      trace(3, 1) {
        span(0) {
          serviceName "elasticsearch"
          resourceName "PutMappingAction"
          operationName "elasticsearch.query"
          spanType DDSpanTypes.ELASTICSEARCH
          tags {
            "$Tags.COMPONENT.key" "elasticsearch-java"
            "$Tags.DB_TYPE.key" "elasticsearch"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "elasticsearch.action" "PutMappingAction"
            "elasticsearch.request" "PutMappingRequest"
            "elasticsearch.request.indices" indexName
            defaultTags()
          }
        }
      }
      trace(4, 1) {
        span(0) {
          serviceName "elasticsearch"
          resourceName "IndexAction"
          operationName "elasticsearch.query"
          spanType DDSpanTypes.ELASTICSEARCH
          tags {
            "$Tags.COMPONENT.key" "elasticsearch-java"
            "$Tags.DB_TYPE.key" "elasticsearch"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "elasticsearch.action" "IndexAction"
            "elasticsearch.request" "IndexRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.request.write.type" indexType
            defaultTags()
          }
        }
      }
      trace(5, 1) {
        span(0) {
          serviceName "elasticsearch"
          resourceName "RefreshAction"
          operationName "elasticsearch.query"
          spanType DDSpanTypes.ELASTICSEARCH
          tags {
            "$Tags.COMPONENT.key" "elasticsearch-java"
            "$Tags.DB_TYPE.key" "elasticsearch"
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
      trace(6, 1) {
        span(0) {
          serviceName "elasticsearch"
          resourceName "SearchAction"
          operationName "elasticsearch.query"
          spanType DDSpanTypes.ELASTICSEARCH
          tags {
            "$Tags.COMPONENT.key" "elasticsearch-java"
            "$Tags.DB_TYPE.key" "elasticsearch"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "elasticsearch.action" "SearchAction"
            "elasticsearch.request" "SearchRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.request.search.types" indexType
            defaultTags()
          }
        }
      }
    }

    cleanup:
    template.deleteIndex(indexName)

    where:
    indexName = "test-index"
    indexType = "test-type"
    id = "1"
  }

  def "test results extractor"() {
    setup:
    template.createIndex(indexName)
    TEST_WRITER.waitForTraces(1)
    testNode.client().admin().cluster().prepareHealth().setWaitForYellowStatus().execute().actionGet(TIMEOUT)
    TEST_WRITER.waitForTraces(2)

    template.index(IndexQueryBuilder.newInstance()
      .withObject(new Doc(id: 1, data: "doc a"))
      .withIndexName(indexName)
      .withId("a")
      .build())
    template.index(IndexQueryBuilder.newInstance()
      .withObject(new Doc(id: 2, data: "doc b"))
      .withIndexName(indexName)
      .withId("b")
      .build())
    template.refresh(indexName)
    TEST_WRITER.waitForTraces(6)
    TEST_WRITER.clear()

    and:
    def query = new NativeSearchQueryBuilder().withIndices(indexName).build()
    def hits = new AtomicLong()
    List<Map<String, Object>> results = []
    def bucketTags = [:]

    when:
    template.query(query, new ResultsExtractor<Doc>() {

      @Override
      Doc extract(SearchResponse response) {
        hits.addAndGet(response.getHits().totalHits())
        results.addAll(response.hits.collect { it.source })
        if (response.getAggregations() != null) {
          InternalNested internalNested = response.getAggregations().get("tag")
          if (internalNested != null) {
            Terms terms = internalNested.getAggregations().get("count_agg")
            Collection<Terms.Bucket> buckets = terms.getBuckets()
            for (Terms.Bucket bucket : buckets) {
              bucketTags.put(Integer.valueOf(bucket.getKeyAsString()), bucket.getDocCount())
            }
          }
        }
        return null
      }
    })

    then:
    hits.get() == 2
    results[0] == [id: "2", data: "doc b"]
    results[1] == [id: "1", data: "doc a"]
    bucketTags == [:]

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          serviceName "elasticsearch"
          resourceName "SearchAction"
          operationName "elasticsearch.query"
          spanType DDSpanTypes.ELASTICSEARCH
          tags {
            "$Tags.COMPONENT.key" "elasticsearch-java"
            "$Tags.DB_TYPE.key" "elasticsearch"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "elasticsearch.action" "SearchAction"
            "elasticsearch.request" "SearchRequest"
            "elasticsearch.request.indices" indexName
            defaultTags()
          }
        }
      }
    }

    cleanup:
    template.deleteIndex(indexName)

    where:
    indexName = "test-index-extract"
  }
}
