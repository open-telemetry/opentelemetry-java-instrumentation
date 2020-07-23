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

import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.trace.attributes.SemanticAttributes
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest
import org.elasticsearch.action.admin.cluster.stats.ClusterStatsRequest
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.io.FileSystemUtils
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.transport.TransportAddress
import org.elasticsearch.index.IndexNotFoundException
import org.elasticsearch.node.Node
import org.elasticsearch.node.NodeBuilder
import org.elasticsearch.transport.RemoteTransportException
import org.elasticsearch.transport.TransportService
import spock.lang.Shared

import static io.opentelemetry.auto.test.utils.TraceUtils.runUnderTrace
import static io.opentelemetry.trace.Span.Kind.CLIENT

class Elasticsearch2TransportClientTest extends AgentTestRunner {
  public static final long TIMEOUT = 10000; // 10 seconds

  @Shared
  TransportAddress tcpPublishAddress
  @Shared
  Node testNode
  @Shared
  File esWorkingDir
  @Shared
  String clusterName = UUID.randomUUID().toString()

  @Shared
  TransportClient client

  def setupSpec() {

    esWorkingDir = File.createTempDir("test-es-working-dir-", "")
    esWorkingDir.deleteOnExit()
    println "ES work dir: $esWorkingDir"

    def settings = Settings.builder()
      .put("path.home", esWorkingDir.path)
      .build()
    testNode = NodeBuilder.newInstance().clusterName(clusterName).settings(settings).build()
    testNode.start()

    tcpPublishAddress = testNode.injector().getInstance(TransportService).boundAddress().publishAddress()

    client = TransportClient.builder().settings(
      Settings.builder()
      // Since we use listeners to close spans this should make our span closing deterministic which is good for tests
        .put("threadpool.listener.size", 1)
        .put("cluster.name", clusterName)
        .build()
    ).build()
    client.addTransportAddress(tcpPublishAddress)
    runUnderTrace("setup") {
      // this may potentially create multiple requests and therefore multiple spans, so we wrap this call
      // into a top level trace to get exactly one trace in the result.
      client.admin().cluster().prepareHealth().setWaitForYellowStatus().execute().actionGet(TIMEOUT)
    }
    TEST_WRITER.waitForTraces(1)
  }

  def cleanupSpec() {
    testNode?.close()
    if (esWorkingDir != null) {
      FileSystemUtils.deleteSubDirectories(esWorkingDir.toPath())
      esWorkingDir.delete()
    }
  }

  def "test elasticsearch status"() {
    setup:
    def result = client.admin().cluster().health(new ClusterHealthRequest(new String[0]))

    def status = result.get().status

    expect:
    status.name() == "GREEN"

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName "ClusterHealthAction"
          spanKind CLIENT
          attributes {
            "${SemanticAttributes.NET_PEER_NAME.key()}" tcpPublishAddress.host == tcpPublishAddress.address ? null : tcpPublishAddress.address
            "${SemanticAttributes.NET_PEER_IP.key()}" tcpPublishAddress.address
            "${SemanticAttributes.NET_PEER_PORT.key()}" tcpPublishAddress.port
            "${SemanticAttributes.DB_TYPE.key()}" "elasticsearch"
            "elasticsearch.action" "ClusterHealthAction"
            "elasticsearch.request" "ClusterHealthRequest"
          }
        }
      }
    }
  }

  def "test elasticsearch stats"() {
    setup:
    def result = client.admin().cluster().clusterStats(new ClusterStatsRequest(new String[0]))

    def status = result.get().status
    def failures = result.get().failures()

    expect:
    status.name() == "GREEN"
    failures == null

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName "ClusterStatsAction"
          spanKind CLIENT
          attributes {
            "${SemanticAttributes.NET_PEER_NAME.key()}" tcpPublishAddress.host == tcpPublishAddress.address ? null : tcpPublishAddress.address
            "${SemanticAttributes.NET_PEER_IP.key()}" tcpPublishAddress.address
            "${SemanticAttributes.NET_PEER_PORT.key()}" tcpPublishAddress.port
            "${SemanticAttributes.DB_TYPE.key()}" "elasticsearch"
            "elasticsearch.action" "ClusterStatsAction"
            "elasticsearch.request" "ClusterStatsRequest"
            "elasticsearch.node.cluster.name" clusterName
          }
        }
      }
    }
  }

  def "test elasticsearch error"() {
    when:
    client.prepareGet(indexName, indexType, id).get()

    then:
    thrown IndexNotFoundException

    and:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName "GetAction"
          spanKind CLIENT
          errored true
          attributes {
            "${SemanticAttributes.DB_TYPE.key()}" "elasticsearch"
            "elasticsearch.action" "GetAction"
            "elasticsearch.request" "GetRequest"
            "elasticsearch.request.indices" indexName
            errorAttributes RemoteTransportException, String
          }
        }
      }
    }

    where:
    indexName = "invalid-index"
    indexType = "test-type"
    id = "1"
  }

  def "test elasticsearch get"() {
    setup:
    assert TEST_WRITER.traces == []
    def indexResult = client.admin().indices().prepareCreate(indexName).get()
    TEST_WRITER.waitForTraces(1)

    expect:
    indexResult.acknowledged
    TEST_WRITER.traces.size() == 1

    when:
    client.admin().cluster().prepareHealth().setWaitForYellowStatus().execute().actionGet(TIMEOUT)
    def emptyResult = client.prepareGet(indexName, indexType, id).get()

    then:
    !emptyResult.isExists()
    emptyResult.id == id
    emptyResult.type == indexType
    emptyResult.index == indexName

    when:
    def createResult = client.prepareIndex(indexName, indexType, id).setSource([:]).get()

    then:
    createResult.id == id
    createResult.type == indexType
    createResult.index == indexName

    when:
    def result = client.prepareGet(indexName, indexType, id).get()

    then:
    result.isExists()
    result.id == id
    result.type == indexType
    result.index == indexName

    and:
    assertTraces(6) {
      sortTraces {
        // IndexAction and PutMappingAction run in separate threads and so their order is not always the same
        if (traces[3][0].name == "IndexAction") {
          def tmp = traces[3]
          traces[3] = traces[4]
          traces[4] = tmp
        }
      }
      trace(0, 1) {
        span(0) {
          operationName "CreateIndexAction"
          spanKind CLIENT
          attributes {
            "${SemanticAttributes.NET_PEER_NAME.key()}" tcpPublishAddress.host == tcpPublishAddress.address ? null : tcpPublishAddress.address
            "${SemanticAttributes.NET_PEER_IP.key()}" tcpPublishAddress.address
            "${SemanticAttributes.NET_PEER_PORT.key()}" tcpPublishAddress.port
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
            "${SemanticAttributes.NET_PEER_NAME.key()}" tcpPublishAddress.host == tcpPublishAddress.address ? null : tcpPublishAddress.address
            "${SemanticAttributes.NET_PEER_IP.key()}" tcpPublishAddress.address
            "${SemanticAttributes.NET_PEER_PORT.key()}" tcpPublishAddress.port
            "${SemanticAttributes.DB_TYPE.key()}" "elasticsearch"
            "elasticsearch.action" "ClusterHealthAction"
            "elasticsearch.request" "ClusterHealthRequest"
          }
        }
      }
      trace(2, 1) {
        span(0) {
          operationName "GetAction"
          spanKind CLIENT
          attributes {
            "${SemanticAttributes.NET_PEER_NAME.key()}" tcpPublishAddress.host == tcpPublishAddress.address ? null : tcpPublishAddress.address
            "${SemanticAttributes.NET_PEER_IP.key()}" tcpPublishAddress.address
            "${SemanticAttributes.NET_PEER_PORT.key()}" tcpPublishAddress.port
            "${SemanticAttributes.DB_TYPE.key()}" "elasticsearch"
            "elasticsearch.action" "GetAction"
            "elasticsearch.request" "GetRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.type" indexType
            "elasticsearch.id" "1"
            "elasticsearch.version"(-1)
          }
        }
      }
      trace(3, 1) {
        span(0) {
          operationName "PutMappingAction"
          spanKind CLIENT
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
          operationName "IndexAction"
          spanKind CLIENT
          attributes {
            "${SemanticAttributes.NET_PEER_NAME.key()}" tcpPublishAddress.host == tcpPublishAddress.address ? null : tcpPublishAddress.address
            "${SemanticAttributes.NET_PEER_IP.key()}" tcpPublishAddress.address
            "${SemanticAttributes.NET_PEER_PORT.key()}" tcpPublishAddress.port
            "${SemanticAttributes.DB_TYPE.key()}" "elasticsearch"
            "elasticsearch.action" "IndexAction"
            "elasticsearch.request" "IndexRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.request.write.type" indexType
          }
        }
      }
      trace(5, 1) {
        span(0) {
          operationName "GetAction"
          spanKind CLIENT
          attributes {
            "${SemanticAttributes.NET_PEER_NAME.key()}" tcpPublishAddress.host == tcpPublishAddress.address ? null : tcpPublishAddress.address
            "${SemanticAttributes.NET_PEER_IP.key()}" tcpPublishAddress.address
            "${SemanticAttributes.NET_PEER_PORT.key()}" tcpPublishAddress.port
            "${SemanticAttributes.DB_TYPE.key()}" "elasticsearch"
            "elasticsearch.action" "GetAction"
            "elasticsearch.request" "GetRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.type" indexType
            "elasticsearch.id" "1"
            "elasticsearch.version" 1
          }
        }
      }
    }

    cleanup:
    client.admin().indices().prepareDelete(indexName).get()

    where:
    indexName = "test-index"
    indexType = "test-type"
    id = "1"
  }
}
