/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.armeria.v1_3

import com.linecorp.armeria.client.WebClient
import com.linecorp.armeria.client.WebClientBuilder
import com.linecorp.armeria.common.HttpMethod
import com.linecorp.armeria.common.HttpRequest
import com.linecorp.armeria.common.RequestHeaders
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import java.util.concurrent.CompletionException
import spock.lang.Shared

abstract class AbstractArmeriaHttpClientTest extends HttpClientTest<HttpRequest> {

  abstract WebClientBuilder configureClient(WebClientBuilder clientBuilder)

  @Shared
  def client = configureClient(WebClient.builder()).build()

  @Override
  HttpRequest buildRequest(String method, URI uri, Map<String, String> headers) {
    return HttpRequest.of(
      RequestHeaders.builder(HttpMethod.valueOf(method), uri.toString())
        .set(headers.entrySet())
        .build())
  }

  @Override
  int sendRequest(HttpRequest request, String method, URI uri, Map<String, String> headers) {
    try {
      return client.execute(request)
        .aggregate()
        .join()
        .status()
        .code()
    } catch (CompletionException e) {
      throw e.cause
    }
  }

  @Override
  void sendRequestWithCallback(HttpRequest request, String method, URI uri, Map<String, String> headers, RequestResult requestResult) {
    client.execute(request).aggregate().whenComplete {response, throwable ->
      requestResult.complete({ response.status().code() }, throwable)
    }
  }

  // Not supported yet: https://github.com/line/armeria/issues/2489
  @Override
  boolean testRedirects() {
    false
  }

  @Override
  boolean testReusedRequest() {
    // armeria requests can't be reused
    false
  }

  @Override
  Set<AttributeKey<?>> httpAttributes(URI uri) {
    Set<AttributeKey<?>> extra = [
      SemanticAttributes.HTTP_HOST,
      SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH,
      SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH,
      SemanticAttributes.HTTP_SCHEME,
      SemanticAttributes.HTTP_TARGET,
    ]
    super.httpAttributes(uri) + extra
  }
}
