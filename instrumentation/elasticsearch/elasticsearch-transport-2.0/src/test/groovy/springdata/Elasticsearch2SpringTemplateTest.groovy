/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package springdata


import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.trace.attributes.SemanticAttributes
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

import static io.opentelemetry.trace.Span.Kind.CLIENT

class Elasticsearch2SpringTemplateTest extends AgentTestRunner {
  public static final long TIMEOUT = 10000; // 10 seconds

  @Shared
  Node testNode
  @Shared
  File esWorkingDir
  @Shared
  String clusterName = UUID.randomUUID().toString()

  @Shared
  ElasticsearchTemplate template

  def setupSpec() {

    esWorkingDir = File.createTempDir("test-es-working-dir-", "")
    esWorkingDir.deleteOnExit()
    println "ES work dir: $esWorkingDir"

    def settings = Settings.builder()
      .put("path.home", esWorkingDir.path)
    // Since we use listeners to close spans this should make our span closing deterministic which is good for tests
      .put("threadpool.listener.size", 1)
      .build()
    testNode = NodeBuilder.newInstance().local(true).clusterName(clusterName).settings(settings).build()
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
          operationName "RefreshAction"
          spanKind CLIENT
          errored true
          errorEvent IndexNotFoundException, "no such index"
          attributes {
            "${SemanticAttributes.DB_TYPE.key()}" "elasticsearch"
            "elasticsearch.action" "RefreshAction"
            "elasticsearch.request" "RefreshRequest"
            "elasticsearch.request.indices" indexName
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
    template.getClient().admin().cluster().prepareHealth().setWaitForYellowStatus().execute().actionGet(TIMEOUT)

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
    assertTraces(6) {
      trace(0, 1) {
        span(0) {
          operationName "CreateIndexAction"
          spanKind CLIENT
          attributes {
            "${SemanticAttributes.DB_TYPE.key()}" "elasticsearch"
            "elasticsearch.action" "CreateIndexAction"
            "elasticsearch.request" "CreateIndexRequest"
            "elasticsearch.request.indices" indexName
          }
        }
      }
      trace(1, 1) {
        span(0) {
          operationName "ClusterHealthAction"
          spanKind CLIENT
          attributes {
            "${SemanticAttributes.DB_TYPE.key()}" "elasticsearch"
            "elasticsearch.action" "ClusterHealthAction"
            "elasticsearch.request" "ClusterHealthRequest"
          }
        }
      }
      trace(2, 1) {
        span(0) {
          operationName "SearchAction"
          spanKind CLIENT
          attributes {
            "${SemanticAttributes.DB_TYPE.key()}" "elasticsearch"
            "elasticsearch.action" "SearchAction"
            "elasticsearch.request" "SearchRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.request.search.types" indexType
          }
        }
      }
      trace(3, 2) {
        span(0) {
          operationName "IndexAction"
          spanKind CLIENT
          attributes {
            "${SemanticAttributes.NET_PEER_NAME.key()}" "local"
            "${SemanticAttributes.NET_PEER_IP.key()}" "0.0.0.0"
            "${SemanticAttributes.NET_PEER_PORT.key()}" 0
            "${SemanticAttributes.DB_TYPE.key()}" "elasticsearch"
            "elasticsearch.action" "IndexAction"
            "elasticsearch.request" "IndexRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.request.write.type" indexType
          }
        }
        span(1) {
          operationName "PutMappingAction"
          spanKind CLIENT
          childOf span(0)
          attributes {
            "${SemanticAttributes.DB_TYPE.key()}" "elasticsearch"
            "elasticsearch.action" "PutMappingAction"
            "elasticsearch.request" "PutMappingRequest"
            "elasticsearch.request.indices" indexName
          }
        }
      }
      trace(4, 1) {
        span(0) {
          operationName "RefreshAction"
          spanKind CLIENT
          attributes {
            "${SemanticAttributes.DB_TYPE.key()}" "elasticsearch"
            "elasticsearch.action" "RefreshAction"
            "elasticsearch.request" "RefreshRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.shard.broadcast.failed" 0
            "elasticsearch.shard.broadcast.successful" 5
            "elasticsearch.shard.broadcast.total" 10
          }
        }
      }
      trace(5, 1) {
        span(0) {
          operationName "SearchAction"
          spanKind CLIENT
          attributes {
            "${SemanticAttributes.DB_TYPE.key()}" "elasticsearch"
            "elasticsearch.action" "SearchAction"
            "elasticsearch.request" "SearchRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.request.search.types" indexType
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
    testNode.client().admin().cluster().prepareHealth().setWaitForYellowStatus().execute().actionGet(TIMEOUT)
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
    TEST_WRITER.waitForTraces(5)
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
          operationName "SearchAction"
          spanKind CLIENT
          attributes {
            "${SemanticAttributes.DB_TYPE.key()}" "elasticsearch"
            "elasticsearch.action" "SearchAction"
            "elasticsearch.request" "SearchRequest"
            "elasticsearch.request.indices" indexName
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
