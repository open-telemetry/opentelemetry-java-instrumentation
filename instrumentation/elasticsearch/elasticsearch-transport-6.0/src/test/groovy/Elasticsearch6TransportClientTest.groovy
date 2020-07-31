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
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.io.FileSystemUtils
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.transport.TransportAddress
import org.elasticsearch.index.IndexNotFoundException
import org.elasticsearch.node.InternalSettingsPreparer
import org.elasticsearch.node.Node
import org.elasticsearch.transport.Netty4Plugin
import org.elasticsearch.transport.RemoteTransportException
import org.elasticsearch.transport.TransportService
import org.elasticsearch.transport.client.PreBuiltTransportClient
import spock.lang.Shared

import static io.opentelemetry.auto.test.utils.TraceUtils.runUnderTrace
import static io.opentelemetry.trace.Span.Kind.CLIENT
import static org.elasticsearch.cluster.ClusterName.CLUSTER_NAME_SETTING

class Elasticsearch6TransportClientTest extends AgentTestRunner {
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
      .put(CLUSTER_NAME_SETTING.getKey(), clusterName)
      .build()
    testNode = new Node(InternalSettingsPreparer.prepareEnvironment(settings, null), [Netty4Plugin])
    testNode.start()
    tcpPublishAddress = testNode.injector().getInstance(TransportService).boundAddress().publishAddress()

    client = new PreBuiltTransportClient(
      Settings.builder()
      // Since we use listeners to close spans this should make our span closing deterministic which is good for tests
        .put("thread_pool.listener.size", 1)
        .put(CLUSTER_NAME_SETTING.getKey(), clusterName)
        .build()
    )
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
    def result = client.admin().cluster().health(new ClusterHealthRequest())

    def status = result.get().status

    expect:
    status.name() == "GREEN"

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName "ClusterHealthAction"
          spanKind CLIENT
          attributes {
            "${SemanticAttributes.NET_PEER_NAME.key()}" "localhost"
            "${SemanticAttributes.NET_PEER_IP.key()}" tcpPublishAddress.address
            "${SemanticAttributes.NET_PEER_PORT.key()}" tcpPublishAddress.port
            "${SemanticAttributes.DB_SYSTEM.key()}" "elasticsearch"
            "elasticsearch.action" "ClusterHealthAction"
            "elasticsearch.request" "ClusterHealthRequest"
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
          errorEvent RemoteTransportException, String
          attributes {
            "${SemanticAttributes.DB_SYSTEM.key()}" "elasticsearch"
            "elasticsearch.action" "GetAction"
            "elasticsearch.request" "GetRequest"
            "elasticsearch.request.indices" indexName
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
    indexResult.index() == indexName
    TEST_WRITER.traces.size() == 1

    when:
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
    createResult.status().status == 201

    when:
    def result = client.prepareGet(indexName, indexType, id).get()

    then:
    result.isExists()
    result.id == id
    result.type == indexType
    result.index == indexName

    and:
    assertTraces(5) {
      sortTraces {
        // IndexAction and PutMappingAction run in separate threads and so their order is not always the same
        if (traces[2][0].name == "IndexAction") {
          def tmp = traces[2]
          traces[2] = traces[3]
          traces[3] = tmp
        }
      }
      trace(0, 1) {
        span(0) {
          operationName "CreateIndexAction"
          spanKind CLIENT
          attributes {
            "${SemanticAttributes.NET_PEER_NAME.key()}" "localhost"
            "${SemanticAttributes.NET_PEER_IP.key()}" tcpPublishAddress.address
            "${SemanticAttributes.NET_PEER_PORT.key()}" tcpPublishAddress.port
            "${SemanticAttributes.DB_SYSTEM.key()}" "elasticsearch"
            "elasticsearch.action" "CreateIndexAction"
            "elasticsearch.request" "CreateIndexRequest"
            "elasticsearch.request.indices" indexName
          }
        }
      }
      trace(1, 1) {
        span(0) {
          operationName "GetAction"
          spanKind CLIENT
          attributes {
            "${SemanticAttributes.NET_PEER_NAME.key()}" "localhost"
            "${SemanticAttributes.NET_PEER_IP.key()}" tcpPublishAddress.address
            "${SemanticAttributes.NET_PEER_PORT.key()}" tcpPublishAddress.port
            "${SemanticAttributes.DB_SYSTEM.key()}" "elasticsearch"
            "elasticsearch.action" "GetAction"
            "elasticsearch.request" "GetRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.type" indexType
            "elasticsearch.id" "1"
            "elasticsearch.version"(-1)
          }
        }
      }
      trace(2, 1) {
        span(0) {
          operationName "PutMappingAction"
          spanKind CLIENT
          attributes {
            "${SemanticAttributes.DB_SYSTEM.key()}" "elasticsearch"
            "elasticsearch.action" "PutMappingAction"
            "elasticsearch.request" "PutMappingRequest"
          }
        }
      }
      trace(3, 1) {
        span(0) {
          operationName "IndexAction"
          spanKind CLIENT
          attributes {
            "${SemanticAttributes.NET_PEER_NAME.key()}" "localhost"
            "${SemanticAttributes.NET_PEER_IP.key()}" tcpPublishAddress.address
            "${SemanticAttributes.NET_PEER_PORT.key()}" tcpPublishAddress.port
            "${SemanticAttributes.DB_SYSTEM.key()}" "elasticsearch"
            "elasticsearch.action" "IndexAction"
            "elasticsearch.request" "IndexRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.request.write.type" indexType
            "elasticsearch.request.write.version"(-3)
            "elasticsearch.response.status" 201
            "elasticsearch.shard.replication.total" 2
            "elasticsearch.shard.replication.successful" 1
            "elasticsearch.shard.replication.failed" 0
          }
        }
      }
      trace(4, 1) {
        span(0) {
          operationName "GetAction"
          spanKind CLIENT
          attributes {
            "${SemanticAttributes.NET_PEER_NAME.key()}" "localhost"
            "${SemanticAttributes.NET_PEER_IP.key()}" tcpPublishAddress.address
            "${SemanticAttributes.NET_PEER_PORT.key()}" tcpPublishAddress.port
            "${SemanticAttributes.DB_SYSTEM.key()}" "elasticsearch"
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
