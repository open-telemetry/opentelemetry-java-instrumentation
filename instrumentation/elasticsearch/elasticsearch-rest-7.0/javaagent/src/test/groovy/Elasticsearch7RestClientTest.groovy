/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */


import groovy.json.JsonSlurper
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import org.apache.http.HttpHost
import org.apache.http.client.config.RequestConfig
import org.apache.http.util.EntityUtils
import org.elasticsearch.client.Request
import org.elasticsearch.client.Response
import org.elasticsearch.client.ResponseListener
import org.elasticsearch.client.RestClient
import org.elasticsearch.client.RestClientBuilder
import org.testcontainers.elasticsearch.ElasticsearchContainer
import spock.lang.Shared

import java.util.concurrent.CountDownLatch

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.api.trace.SpanKind.INTERNAL

class Elasticsearch7RestClientTest extends AgentInstrumentationSpecification {
  @Shared
  ElasticsearchContainer elasticsearch

  @Shared
  HttpHost httpHost

  @Shared
  RestClient client

  def setupSpec() {
    elasticsearch = new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch-oss:7.10.2")
    elasticsearch.start()

    httpHost = HttpHost.create(elasticsearch.getHttpHostAddress())
    client = RestClient.builder(httpHost)
      .setRequestConfigCallback(new RestClientBuilder.RequestConfigCallback() {
        @Override
        RequestConfig.Builder customizeRequestConfig(RequestConfig.Builder builder) {
          return builder.setConnectTimeout(Integer.MAX_VALUE).setSocketTimeout(Integer.MAX_VALUE)
        }
      })
      .build()
  }

  def cleanupSpec() {
    elasticsearch.stop()
  }

  def "test elasticsearch status"() {
    setup:
    Response response = client.performRequest(new Request("GET", "_cluster/health"))

    Map result = new JsonSlurper().parseText(EntityUtils.toString(response.entity))

    expect:
    result.status == "green"

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "GET _cluster/health"
          kind CLIENT
          hasNoParent()
          attributes {
            "${SemanticAttributes.DB_SYSTEM.key}" "elasticsearch"
            "${SemanticAttributes.DB_OPERATION.key}" "GET _cluster/health"
            "${SemanticAttributes.NET_PEER_NAME.key}" httpHost.hostName
            "${SemanticAttributes.NET_PEER_PORT.key}" httpHost.port
          }
        }
      }
    }
  }

  def "test elasticsearch status async"() {
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
    Map result = new JsonSlurper().parseText(EntityUtils.toString(requestResponse.entity))

    expect:
    result.status == "green"

    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          name "parent"
          kind INTERNAL
          hasNoParent()
        }
        span(1) {
          name "GET _cluster/health"
          kind CLIENT
          childOf(span(0))
          attributes {
            "${SemanticAttributes.DB_SYSTEM.key}" "elasticsearch"
            "${SemanticAttributes.DB_OPERATION.key}" "GET _cluster/health"
            "${SemanticAttributes.NET_PEER_NAME.key}" httpHost.hostName
            "${SemanticAttributes.NET_PEER_PORT.key}" httpHost.port
          }
        }
        span(2) {
          name "callback"
          kind INTERNAL
          childOf(span(0))
        }
      }
    }
  }
}
