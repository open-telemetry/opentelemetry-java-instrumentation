/*
 * Copyright 2020, OpenTelemetry Authors
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
import groovy.json.JsonSlurper
import io.opentelemetry.auto.bootstrap.instrumentation.decorator.HttpClientDecorator
import io.opentelemetry.auto.instrumentation.api.MoreTags
import io.opentelemetry.auto.instrumentation.api.Tags
import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.auto.test.utils.PortUtils
import org.apache.http.HttpHost
import org.apache.http.client.config.RequestConfig
import org.apache.http.util.EntityUtils
import org.elasticsearch.client.Response
import org.elasticsearch.client.RestClient
import org.elasticsearch.client.RestClientBuilder
import org.elasticsearch.common.io.FileSystemUtils
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.env.Environment
import org.elasticsearch.node.Node
import org.elasticsearch.node.internal.InternalSettingsPreparer
import org.elasticsearch.transport.Netty3Plugin
import spock.lang.Shared

import static io.opentelemetry.trace.Span.Kind.CLIENT
import static org.elasticsearch.cluster.ClusterName.CLUSTER_NAME_SETTING

class Elasticsearch5RestClientTest extends AgentTestRunner {
  @Shared
  int httpPort
  @Shared
  int tcpPort
  @Shared
  Node testNode
  @Shared
  File esWorkingDir

  @Shared
  static RestClient client

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
      .put("transport.type", "netty3")
      .put("http.type", "netty3")
      .put(CLUSTER_NAME_SETTING.getKey(), "test-cluster")
      .build()
    testNode = new Node(new Environment(InternalSettingsPreparer.prepareSettings(settings)), [Netty3Plugin])
    testNode.start()

    client = RestClient.builder(new HttpHost("localhost", httpPort))
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

    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          operationName "elasticsearch.rest.query"
          spanKind CLIENT
          parent()
          tags {
            "$MoreTags.SERVICE_NAME" "elasticsearch"
            "$Tags.COMPONENT" "elasticsearch-java"
            "$MoreTags.NET_PEER_NAME" "localhost"
            "$MoreTags.NET_PEER_PORT" httpPort
            "$Tags.HTTP_URL" "_cluster/health"
            "$Tags.HTTP_METHOD" "GET"
            "$Tags.DB_TYPE" "elasticsearch"
          }
        }
        span(1) {
          operationName expectedOperationName("GET")
          spanKind CLIENT
          childOf span(0)
          tags {
            "$Tags.COMPONENT" "apache-httpasyncclient"
            "$Tags.HTTP_URL" "_cluster/health"
            "$Tags.HTTP_METHOD" "GET"
            "$Tags.HTTP_STATUS" 200
          }
        }
      }
    }
  }

  String expectedOperationName(String method) {
    return method != null ? "HTTP $method" : HttpClientDecorator.DEFAULT_SPAN_NAME
  }
}
