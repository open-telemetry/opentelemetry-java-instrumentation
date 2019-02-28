import com.anotherchrisberry.spock.extensions.retry.RetryOnFailure
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.utils.PortUtils
import datadog.trace.api.DDSpanTypes
import io.opentracing.tag.Tags
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
import org.elasticsearch.transport.client.PreBuiltTransportClient
import spock.lang.Shared

import static datadog.trace.agent.test.utils.TraceUtils.runUnderTrace
import static org.elasticsearch.cluster.ClusterName.CLUSTER_NAME_SETTING

@RetryOnFailure(times = 3, delaySeconds = 1)
class Elasticsearch6TransportClientTest extends AgentTestRunner {
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
  TransportClient client

  def setupSpec() {
    httpPort = PortUtils.randomOpenPort()
    tcpPort = PortUtils.randomOpenPort()

    esWorkingDir = File.createTempDir("test-es-working-dir-", "")
    esWorkingDir.deleteOnExit()
    println "ES work dir: $esWorkingDir"

    def settings = Settings.builder()
      .put("path.home", esWorkingDir.path)
      .put("http.port", httpPort)
      .put("transport.tcp.port", tcpPort)
      .put(CLUSTER_NAME_SETTING.getKey(), "test-cluster")
      .build()
    testNode = new Node(InternalSettingsPreparer.prepareEnvironment(settings, null), [Netty4Plugin])
    testNode.start()

    client = new PreBuiltTransportClient(
      Settings.builder()
      // Since we use listeners to close spans this should make our span closing deterministic which is good for tests
        .put("thread_pool.listener.size", 1)
        .put(CLUSTER_NAME_SETTING.getKey(), "test-cluster")
        .build()
    )
    client.addTransportAddress(new TransportAddress(InetAddress.getByName("localhost"), tcpPort))
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
          serviceName "elasticsearch"
          resourceName "ClusterHealthAction"
          operationName "elasticsearch.query"
          spanType DDSpanTypes.ELASTICSEARCH
          tags {
            "$Tags.COMPONENT.key" "elasticsearch-java"
            "$Tags.DB_TYPE.key" "elasticsearch"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$Tags.PEER_HOSTNAME.key" "localhost"
            "$Tags.PEER_HOST_IPV4.key" "127.0.0.1"
            "$Tags.PEER_PORT.key" tcpPort
            "elasticsearch.action" "ClusterHealthAction"
            "elasticsearch.request" "ClusterHealthRequest"
            defaultTags()
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
          serviceName "elasticsearch"
          resourceName "GetAction"
          operationName "elasticsearch.query"
          spanType DDSpanTypes.ELASTICSEARCH
          errored true
          tags {
            "$Tags.COMPONENT.key" "elasticsearch-java"
            "$Tags.DB_TYPE.key" "elasticsearch"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "elasticsearch.action" "GetAction"
            "elasticsearch.request" "GetRequest"
            "elasticsearch.request.indices" indexName
            errorTags RemoteTransportException, String
            defaultTags()
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
    assert TEST_WRITER == []
    def indexResult = client.admin().indices().prepareCreate(indexName).get()
    TEST_WRITER.waitForTraces(1)

    expect:
    indexResult.index() == indexName
    TEST_WRITER.size() == 1

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
    // IndexAction and PutMappingAction run in separate threads and order in which
    // these spans are closed is not defined. So we force the order if it is wrong.
    if (TEST_WRITER[2][0].resourceName == "IndexAction") {
      def tmp = TEST_WRITER[2]
      TEST_WRITER[2] = TEST_WRITER[3]
      TEST_WRITER[3] = tmp
    }
    assertTraces(5) {
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
            "$Tags.PEER_HOSTNAME.key" "localhost"
            "$Tags.PEER_HOST_IPV4.key" "127.0.0.1"
            "$Tags.PEER_PORT.key" tcpPort
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
          resourceName "GetAction"
          operationName "elasticsearch.query"
          spanType DDSpanTypes.ELASTICSEARCH
          tags {
            "$Tags.COMPONENT.key" "elasticsearch-java"
            "$Tags.DB_TYPE.key" "elasticsearch"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$Tags.PEER_HOSTNAME.key" "localhost"
            "$Tags.PEER_HOST_IPV4.key" "127.0.0.1"
            "$Tags.PEER_PORT.key" tcpPort
            "elasticsearch.action" "GetAction"
            "elasticsearch.request" "GetRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.type" indexType
            "elasticsearch.id" "1"
            "elasticsearch.version"(-1)
            defaultTags()
          }
        }
      }
      trace(2, 1) {
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
      trace(3, 1) {
        span(0) {
          serviceName "elasticsearch"
          resourceName "IndexAction"
          operationName "elasticsearch.query"
          spanType DDSpanTypes.ELASTICSEARCH
          tags {
            "$Tags.COMPONENT.key" "elasticsearch-java"
            "$Tags.DB_TYPE.key" "elasticsearch"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$Tags.PEER_HOSTNAME.key" "localhost"
            "$Tags.PEER_HOST_IPV4.key" "127.0.0.1"
            "$Tags.PEER_PORT.key" tcpPort
            "elasticsearch.action" "IndexAction"
            "elasticsearch.request" "IndexRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.request.write.type" indexType
            "elasticsearch.request.write.version"(-3)
            "elasticsearch.response.status" 201
            "elasticsearch.shard.replication.total" 2
            "elasticsearch.shard.replication.successful" 1
            "elasticsearch.shard.replication.failed" 0
            defaultTags()
          }
        }
      }
      trace(4, 1) {
        span(0) {
          serviceName "elasticsearch"
          resourceName "GetAction"
          operationName "elasticsearch.query"
          spanType DDSpanTypes.ELASTICSEARCH
          tags {
            "$Tags.COMPONENT.key" "elasticsearch-java"
            "$Tags.DB_TYPE.key" "elasticsearch"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$Tags.PEER_HOSTNAME.key" "localhost"
            "$Tags.PEER_HOST_IPV4.key" "127.0.0.1"
            "$Tags.PEER_PORT.key" tcpPort
            "elasticsearch.action" "GetAction"
            "elasticsearch.request" "GetRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.type" indexType
            "elasticsearch.id" "1"
            "elasticsearch.version" 1
            defaultTags()
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
