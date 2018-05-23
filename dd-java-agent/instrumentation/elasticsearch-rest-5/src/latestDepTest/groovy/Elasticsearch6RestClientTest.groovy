import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.TestUtils
import groovy.json.JsonSlurper
import io.opentracing.tag.Tags
import org.apache.http.HttpHost
import org.apache.http.client.config.RequestConfig
import org.apache.http.util.EntityUtils
import org.elasticsearch.client.Response
import org.elasticsearch.client.RestClient
import org.elasticsearch.client.RestClientBuilder
import org.elasticsearch.common.io.FileSystemUtils
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.node.InternalSettingsPreparer
import org.elasticsearch.node.Node
import org.elasticsearch.transport.Netty4Plugin
import spock.lang.Shared

import static datadog.trace.agent.test.ListWriterAssert.assertTraces

class Elasticsearch6RestClientTest extends AgentTestRunner {
  static {
    System.setProperty("dd.integration.elasticsearch.enabled", "true")
  }

  static final int HTTP_PORT = TestUtils.randomOpenPort()
  static final int TCP_PORT = TestUtils.randomOpenPort()

  @Shared
  static Node testNode
  static File esWorkingDir

  @Shared
  static RestClient client

  def setupSpec() {
    esWorkingDir = File.createTempFile("test-es-working-dir-", "")
    esWorkingDir.delete()
    esWorkingDir.mkdir()
    esWorkingDir.deleteOnExit()
    println "ES work dir: $esWorkingDir"

    def settings = Settings.builder()
      .put("path.home", esWorkingDir.path)
      .put("http.port", HTTP_PORT)
      .put("transport.tcp.port", TCP_PORT)
      .put("cluster.name", "test-cluster")
      .build()
    testNode = new Node(InternalSettingsPreparer.prepareEnvironment(settings, null), [Netty4Plugin])
    testNode.start()

    client = RestClient.builder(new HttpHost("localhost", HTTP_PORT))
      .setMaxRetryTimeoutMillis(Integer.MAX_VALUE)
      .setRequestConfigCallback(new RestClientBuilder.RequestConfigCallback() {
      @Override
      RequestConfig.Builder customizeRequestConfig(RequestConfig.Builder builder) {
        return builder.setConnectTimeout(Integer.MAX_VALUE).setSocketTimeout(Integer.MAX_VALUE)
      }
    })
      .build()

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
    Response response = client.performRequest("GET", "_cluster/health")

    Map result = new JsonSlurper().parseText(EntityUtils.toString(response.entity))

    expect:
    result.status == "green"

    assertTraces(TEST_WRITER, 2) {
      trace(0, 1) {
        span(0) {
          serviceName "elasticsearch"
          resourceName "ClusterHealthAction"
          operationName "elasticsearch.query"
          spanType null
          parent()
          tags {
            "$Tags.COMPONENT.key" "elasticsearch-java"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "elasticsearch.action" "ClusterHealthAction"
            "elasticsearch.request" "ClusterHealthRequest"
            defaultTags()
          }
        }
      }
      trace(1, 1) {
        span(0) {
          serviceName "elasticsearch"
          resourceName "GET _cluster/health"
          operationName "elasticsearch.rest.query"
          spanType null
          parent()
          tags {
            "$Tags.COMPONENT.key" "elasticsearch-java"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_URL.key" "_cluster/health"
            "$Tags.PEER_HOSTNAME.key" "localhost"
            "$Tags.PEER_PORT.key" HTTP_PORT
            defaultTags()
          }
        }
      }
    }
  }
}
