/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */


import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch.core.InfoResponse
import co.elastic.clients.json.jackson.JacksonJsonpMapper
import co.elastic.clients.transport.ElasticsearchTransport
import co.elastic.clients.transport.rest_client.RestClientTransport
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import org.apache.http.HttpHost
import org.apache.http.client.config.RequestConfig
import org.elasticsearch.client.RestClient
import org.elasticsearch.client.RestClientBuilder
import org.testcontainers.elasticsearch.ElasticsearchContainer
import spock.lang.Shared

import static io.opentelemetry.api.trace.SpanKind.CLIENT

class ElasticsearchClient8Test extends AgentInstrumentationSpecification {
  @Shared
  ElasticsearchContainer elasticsearch

  @Shared
  HttpHost httpHost

  @Shared
  ElasticsearchClient client

  def setupSpec() {
    elasticsearch = new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:7.17.2")
    // limit memory usage
    elasticsearch.withEnv("ES_JAVA_OPTS", "-Xmx256m -Xms256m")
    elasticsearch.start()

    httpHost = HttpHost.create(elasticsearch.getHttpHostAddress())


    RestClient restClient = RestClient.builder(httpHost)
        .setRequestConfigCallback(new RestClientBuilder.RequestConfigCallback() {
          @Override
          RequestConfig.Builder customizeRequestConfig(RequestConfig.Builder builder) {
            return builder.setConnectTimeout(Integer.MAX_VALUE).setSocketTimeout(Integer.MAX_VALUE)
          }
        })
        .build()

    ElasticsearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper())
    client = new ElasticsearchClient(transport)
  }

  def cleanupSpec() {
    elasticsearch.stop()
  }

  def "test elasticsearch status"() {
    setup:
    InfoResponse response = client.info()

    expect:
    response.version().number() == "7.17.2"

    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "info"
          kind CLIENT
          hasNoParent()
          attributes {
            "$SemanticAttributes.DB_SYSTEM" "elasticsearch"
            "$SemanticAttributes.DB_OPERATION" "info"
            "$SemanticAttributes.HTTP_METHOD" "GET"
            "$SemanticAttributes.HTTP_URL" "${httpHost.toURI()}/"
          }
        }
        span(1) {
          name "GET"
          kind CLIENT
          childOf span(0)
          attributes {
            "$SemanticAttributes.NET_PEER_NAME" httpHost.hostName
            "$SemanticAttributes.NET_PEER_PORT" httpHost.port
            "$SemanticAttributes.HTTP_METHOD" "GET"
            "net.protocol.name" "http"
            "net.protocol.version" "1.1"
            "$SemanticAttributes.HTTP_URL" "${httpHost.toURI()}/"
            "$SemanticAttributes.HTTP_STATUS_CODE" 200
            "$SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH" Long
            "$SemanticAttributes.USER_AGENT_ORIGINAL" "elastic-java/8.0.0 (Java/${System.getProperty("java.version")})"
          }
        }
      }
    }
  }

  def "test elasticsearch create index"() {
    setup:
    client.index { r ->
      r.id("test-id")
          .index("test-index")
          .document(new TestDoc("test-value-a", 123))
          .timeout(t -> t.time("1s"))
    }

    expect:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "index"
          kind CLIENT
          hasNoParent()
          attributes {
            "$SemanticAttributes.DB_SYSTEM" "elasticsearch"
            "$SemanticAttributes.DB_OPERATION" "index"
            "$SemanticAttributes.HTTP_METHOD" "PUT"
            "$SemanticAttributes.HTTP_URL" "${httpHost.toURI()}/test-index/_doc/test-id?timeout=1s"
            "db.elasticsearch.path_parts.index" "test-index"
            "db.elasticsearch.path_parts.id" "test-id"
          }
        }
        span(1) {
          name "PUT"
          kind CLIENT
          childOf span(0)
          attributes {
            "$SemanticAttributes.NET_PEER_NAME" httpHost.hostName
            "$SemanticAttributes.NET_PEER_PORT" httpHost.port
            "$SemanticAttributes.HTTP_METHOD" "PUT"
            "net.protocol.name" "http"
            "net.protocol.version" "1.1"
            "$SemanticAttributes.HTTP_URL" "${httpHost.toURI()}/test-index/_doc/test-id?timeout=1s"
            "$SemanticAttributes.HTTP_STATUS_CODE" 201
            "$SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH" Long
            "$SemanticAttributes.USER_AGENT_ORIGINAL" "elastic-java/8.0.0 (Java/${System.getProperty("java.version")})"
          }
        }
      }
    }
  }
}
