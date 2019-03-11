package springdata

import com.anotherchrisberry.spock.extensions.retry.RetryOnFailure
import com.google.common.collect.ImmutableSet
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.utils.PortUtils
import datadog.trace.api.DDSpanTypes
import io.opentracing.tag.Tags
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.common.io.FileSystemUtils
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.env.Environment
import org.elasticsearch.index.IndexNotFoundException
import org.elasticsearch.node.InternalSettingsPreparer
import org.elasticsearch.node.Node
import org.elasticsearch.search.aggregations.bucket.nested.InternalNested
import org.elasticsearch.search.aggregations.bucket.terms.Terms
import org.elasticsearch.transport.Netty3Plugin
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate
import org.springframework.data.elasticsearch.core.ResultsExtractor
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder
import spock.lang.Shared

import java.util.concurrent.atomic.AtomicLong

import static datadog.trace.agent.test.utils.TraceUtils.runUnderTrace
import static org.elasticsearch.cluster.ClusterName.CLUSTER_NAME_SETTING

@RetryOnFailure(times = 3, delaySeconds = 1)
class Elasticsearch53SpringTemplateTest extends AgentTestRunner {
  public static final long TIMEOUT = 10000; // 10 seconds

  // Some ES actions are not caused by clients and seem to just happen from time to time.
  // We will just ignore these actions in traces.
  // TODO: check if other ES tests need this protection and potentially pull this into global class
  public static final Set<String> IGNORED_ACTIONS = ImmutableSet.of("NodesStatsAction", "IndicesStatsAction")

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
      .put("thread_pool.listener.size", 1)
      .put("http.port", httpPort)
      .put("transport.tcp.port", tcpPort)
      .put("transport.type", "netty3")
      .put("http.type", "netty3")
      .put(CLUSTER_NAME_SETTING.getKey(), "test-cluster")
      .build()
    testNode = new Node(new Environment(InternalSettingsPreparer.prepareSettings(settings)), [Netty3Plugin])
    testNode.start()
    runUnderTrace("setup") {
      // this may potentially create multiple requests and therefore multiple spans, so we wrap this call
      // into a top level trace to get exactly one trace in the result.
      testNode.client().admin().cluster().prepareHealth().setWaitForYellowStatus().execute().actionGet(TIMEOUT)
    }
    TEST_WRITER.waitForTraces(1)

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
    // FIXME: it looks like proper approach is to provide TEST_WRITER with an API to filter traces as they are written
    TEST_WRITER.waitForTraces(7)
    filterIgnoredActions()
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
            "elasticsearch.request.write.version"(-3)
            "elasticsearch.response.status" 201
            "elasticsearch.shard.replication.failed" 0
            "elasticsearch.shard.replication.successful" 1
            "elasticsearch.shard.replication.total" 2
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

  void filterIgnoredActions() {
    for (int i = 0; i < TEST_WRITER.size(); i++) {
      if (IGNORED_ACTIONS.contains(TEST_WRITER[i][0].getResourceName())) {
        TEST_WRITER.remove(i)
      }
    }
  }
}
