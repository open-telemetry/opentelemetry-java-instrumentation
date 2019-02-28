import com.anotherchrisberry.spock.extensions.retry.RetryOnFailure
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.utils.PortUtils
import datadog.trace.api.DDSpanTypes
import io.opentracing.tag.Tags
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.io.FileSystemUtils
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.transport.InetSocketTransportAddress
import org.elasticsearch.index.IndexNotFoundException
import org.elasticsearch.node.Node
import org.elasticsearch.node.NodeBuilder
import org.elasticsearch.transport.RemoteTransportException
import spock.lang.Shared

import static datadog.trace.agent.test.utils.TraceUtils.runUnderTrace

@RetryOnFailure(times = 3, delaySeconds = 1)
class Elasticsearch2TransportClientTest extends AgentTestRunner {
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
      .build()
    testNode = NodeBuilder.newInstance().clusterName("test-cluster").settings(settings).build()
    testNode.start()

    client = TransportClient.builder().settings(
      Settings.builder()
      // Since we use listeners to close spans this should make our span closing deterministic which is good for tests
        .put("threadpool.listener.size", 1)
        .put("cluster.name", "test-cluster")
        .build()
    ).build()
    client.addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName("localhost"), tcpPort))
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
          serviceName "elasticsearch"
          resourceName "ClusterHealthAction"
          operationName "elasticsearch.query"
          spanType DDSpanTypes.ELASTICSEARCH
          tags {
            "$Tags.COMPONENT.key" "elasticsearch-java"
            "$Tags.DB_TYPE.key" "elasticsearch"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$Tags.PEER_HOSTNAME.key" "127.0.0.1"
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
    indexResult.acknowledged
    TEST_WRITER.size() == 1

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
    // IndexAction and PutMappingAction run in separate threads and order in which
    // these spans are closed is not defined. So we force the order if it is wrong.
    if (TEST_WRITER[3][0].resourceName == "IndexAction") {
      def tmp = TEST_WRITER[3]
      TEST_WRITER[3] = TEST_WRITER[4]
      TEST_WRITER[4] = tmp
    }
    assertTraces(6) {
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
            "$Tags.PEER_HOSTNAME.key" "127.0.0.1"
            "$Tags.PEER_HOST_IPV4.key" "127.0.0.1"
            "$Tags.PEER_PORT.key" tcpPort
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
            "$Tags.PEER_HOSTNAME.key" "127.0.0.1"
            "$Tags.PEER_HOST_IPV4.key" "127.0.0.1"
            "$Tags.PEER_PORT.key" tcpPort
            defaultTags()
          }
        }
      }
      trace(2, 1) {
        span(0) {
          serviceName "elasticsearch"
          resourceName "GetAction"
          operationName "elasticsearch.query"
          spanType DDSpanTypes.ELASTICSEARCH
          tags {
            "$Tags.COMPONENT.key" "elasticsearch-java"
            "$Tags.DB_TYPE.key" "elasticsearch"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$Tags.PEER_HOSTNAME.key" "127.0.0.1"
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
            "$Tags.PEER_HOSTNAME.key" "127.0.0.1"
            "$Tags.PEER_HOST_IPV4.key" "127.0.0.1"
            "$Tags.PEER_PORT.key" tcpPort
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
          resourceName "GetAction"
          operationName "elasticsearch.query"
          spanType DDSpanTypes.ELASTICSEARCH
          tags {
            "$Tags.COMPONENT.key" "elasticsearch-java"
            "$Tags.DB_TYPE.key" "elasticsearch"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$Tags.PEER_HOSTNAME.key" "127.0.0.1"
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
