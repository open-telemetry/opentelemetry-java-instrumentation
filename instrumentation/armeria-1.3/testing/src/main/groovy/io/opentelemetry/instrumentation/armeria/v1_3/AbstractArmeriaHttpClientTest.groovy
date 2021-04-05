/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.armeria.v1_3

import com.linecorp.armeria.client.WebClient
import com.linecorp.armeria.client.WebClientBuilder
import com.linecorp.armeria.common.AggregatedHttpResponse
import com.linecorp.armeria.common.HttpMethod
import com.linecorp.armeria.common.HttpRequest
import com.linecorp.armeria.common.RequestHeaders
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import java.util.concurrent.CompletionException
import java.util.function.Consumer
import spock.lang.Shared

abstract class AbstractArmeriaHttpClientTest extends HttpClientTest {

  abstract WebClientBuilder configureClient(WebClientBuilder clientBuilder)

  @Shared
  def client = configureClient(WebClient.builder()).build()

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers = [:]) {
    AggregatedHttpResponse response
    try {
      response = client.execute(buildRequest(method, uri, headers)).aggregate().join()
    } catch(CompletionException e) {
      throw e.cause
    }
    return response.status().code()
  }

  @Override
  void doRequestAsync(String method, URI uri, Map<String, String> headers = [:], Consumer<Integer> callback) {
    client.execute(buildRequest(method, uri, headers)).aggregate().thenAccept {
      callback.accept(it.status().code())
    }
  }

  private static HttpRequest buildRequest(String method, URI uri, Map<String, String> headers = [:]) {
    return HttpRequest.of(
      RequestHeaders.builder(HttpMethod.valueOf(method), uri.toString())
        .set(headers.entrySet())
        .build())
  }

  // Not supported yet: https://github.com/line/armeria/issues/2489
  @Override
  boolean testRedirects() {
    false
  }

  // TODO(anuraaga): Enable after fixing the test https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/2344
  @Override
  boolean testRemoteConnection() {
    false
  }

  @Override
  List<AttributeKey<?>> extraAttributes() {
    [
      SemanticAttributes.HTTP_HOST,
      SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH,
      SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH,
      SemanticAttributes.HTTP_SCHEME,
      SemanticAttributes.HTTP_TARGET,
    ]
  }
}
