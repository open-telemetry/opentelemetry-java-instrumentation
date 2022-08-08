/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import groovy.json.JsonSlurper
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import org.apache.http.HttpHost
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.client.CredentialsProvider
import org.apache.http.conn.ssl.TrustAllStrategy
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.ssl.SSLContextBuilder
import org.apache.http.util.EntityUtils
import org.opensearch.client.Request
import org.opensearch.client.Response
import org.opensearch.client.ResponseListener
import org.opensearch.client.RestClient
import org.opensearch.testcontainers.OpensearchContainer
import org.testcontainers.utility.DockerImageName
import spock.lang.Shared

import javax.net.ssl.SSLContext
import java.util.concurrent.CountDownLatch

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.api.trace.SpanKind.INTERNAL

class OpenSearchRestTest extends AgentInstrumentationSpecification {
  @Shared
  OpensearchContainer opensearch

  @Shared
  HttpHost httpHost

  @Shared
  RestClient client

  def setupSpec() {
    opensearch = new OpensearchContainer(DockerImageName.parse("opensearchproject/opensearch:1.3.6")).withSecurityEnabled()
    // limit memory usage
    opensearch.withEnv("OPENSEARCH_JAVA_OPTS", "-Xmx256m -Xms256m")
    opensearch.start()

    final CredentialsProvider credentialsProvider = new BasicCredentialsProvider()
    credentialsProvider.setCredentials(AuthScope.ANY,
      new UsernamePasswordCredentials(opensearch.getUsername(), opensearch.getPassword()))

    final SSLContext sslContext = SSLContextBuilder.create()
      .loadTrustMaterial(null, new TrustAllStrategy())
      .build()

    httpHost = HttpHost.create(opensearch.getHttpHostAddress())
    client = RestClient.builder(httpHost)
      .setHttpClientConfigCallback(httpClientBuilder -> {
        return httpClientBuilder
          .setSSLContext(sslContext)
          .setDefaultCredentialsProvider(credentialsProvider)
      })
      .build()
  }

  def cleanupSpec() {
    opensearch.stop()
  }

  def "test opensearch status"() {
    setup:
    Response response = client.performRequest(new Request("GET", "_cluster/health"))

    Map result = new JsonSlurper().parseText(EntityUtils.toString(response.entity))

    expect:
    result.status == "green"

    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "GET"
          kind CLIENT
          hasNoParent()
          attributes {
            "$SemanticAttributes.DB_SYSTEM" "opensearch"
            "$SemanticAttributes.DB_OPERATION" "GET"
            "$SemanticAttributes.DB_STATEMENT" "GET _cluster/health"
            "$SemanticAttributes.NET_TRANSPORT" SemanticAttributes.NetTransportValues.IP_TCP
          }
        }
        span(1) {
          name "HTTP GET"
          kind CLIENT
          childOf span(0)
          attributes {
            "$SemanticAttributes.NET_TRANSPORT" SemanticAttributes.NetTransportValues.IP_TCP
            "$SemanticAttributes.NET_PEER_NAME" httpHost.hostName
            "$SemanticAttributes.NET_PEER_PORT" httpHost.port
            "$SemanticAttributes.HTTP_METHOD" "GET"
            "$SemanticAttributes.HTTP_FLAVOR" SemanticAttributes.HttpFlavorValues.HTTP_1_1
            "$SemanticAttributes.HTTP_URL" "${httpHost.toURI()}/_cluster/health"
            "$SemanticAttributes.HTTP_STATUS_CODE" 200
            "$SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH" Long
          }
        }
      }
    }
  }

  def "test opensearch status async"() {
    setup:
    Response requestResponse = null
    Exception exception = null
    CountDownLatch countDownLatch = new CountDownLatch(1)
    ResponseListener responseListener = new ResponseListener() {
      @Override
      void onSuccess(Response response) {
        runWithSpan("callback") {
          requestResponse = response
          countDownLatch.countDown()
        }
      }

      @Override
      void onFailure(Exception e) {
        runWithSpan("callback") {
          exception = e
          countDownLatch.countDown()
        }
      }
    }
    runWithSpan("parent") {
      client.performRequestAsync(new Request("GET", "_cluster/health"), responseListener)
    }
    countDownLatch.await()

    if (exception != null) {
      throw exception
    }
    Map result = new JsonSlurper().parseText(EntityUtils.toString(requestResponse.entity)) as Map

    expect:
    result.status == "green"

    assertTraces(1) {
      trace(0, 4) {
        span(0) {
          name "parent"
          kind INTERNAL
          hasNoParent()
        }
        span(1) {
          name "GET"
          kind CLIENT
          childOf(span(0))
          attributes {
            "$SemanticAttributes.DB_SYSTEM" "opensearch"
            "$SemanticAttributes.DB_OPERATION" "GET"
            "$SemanticAttributes.DB_STATEMENT" "GET _cluster/health"
            "$SemanticAttributes.NET_TRANSPORT" SemanticAttributes.NetTransportValues.IP_TCP
          }
        }
        span(2) {
          name "HTTP GET"
          kind CLIENT
          childOf span(1)
          attributes {
            "$SemanticAttributes.NET_TRANSPORT" SemanticAttributes.NetTransportValues.IP_TCP
            "$SemanticAttributes.NET_PEER_NAME" httpHost.hostName
            "$SemanticAttributes.NET_PEER_PORT" httpHost.port
            "$SemanticAttributes.HTTP_METHOD" "GET"
            "$SemanticAttributes.HTTP_FLAVOR" SemanticAttributes.HttpFlavorValues.HTTP_1_1
            "$SemanticAttributes.HTTP_URL" "${httpHost.toURI()}/_cluster/health"
            "$SemanticAttributes.HTTP_STATUS_CODE" 200
            "$SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH" 415
          }
        }
        span(3) {
          name "callback"
          kind INTERNAL
          childOf(span(0))
        }
      }
    }
  }
}
