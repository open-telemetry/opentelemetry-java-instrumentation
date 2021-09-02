/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */


import com.google.api.client.http.GenericUrl
import com.google.api.client.http.HttpRequest
import com.google.api.client.http.HttpResponse
import com.google.api.client.http.javanet.NetHttpTransport
import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import spock.lang.Shared

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.api.trace.StatusCode.ERROR
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_TCP

abstract class AbstractGoogleHttpClientTest extends HttpClientTest<HttpRequest> implements AgentTestTrait {

  @Shared
  def requestFactory = new NetHttpTransport().createRequestFactory()

  @Override
  boolean testCallback() {
    // executeAsync does not actually allow asynchronous execution since it returns a standard
    // Future which cannot have callbacks attached. We instrument execute and executeAsync
    // differently so test both but do not need to run our normal asynchronous tests, which check
    // context propagation, as there is no possible context propagation.
    return false
  }

  @Override
  HttpRequest buildRequest(String method, URI uri, Map<String, String> headers) {
    def genericUrl = new GenericUrl(uri)

    def request = requestFactory.buildRequest(method, genericUrl, null)
    request.connectTimeout = CONNECT_TIMEOUT_MS

    // GenericData::putAll method converts all known http headers to List<String>
    // and lowercase all other headers
    def ci = request.getHeaders().getClassInfo()
    request.getHeaders().putAll(headers.collectEntries { name, value
      -> [(name): (ci.getFieldInfo(name) != null ? [value] : value.toLowerCase())]
    })

    request.setThrowExceptionOnExecuteError(false)
    return request
  }

  @Override
  int sendRequest(HttpRequest request, String method, URI uri, Map<String, String> headers) {
    return sendRequest(request).getStatusCode()
  }

  abstract HttpResponse sendRequest(HttpRequest request)

  @Override
  boolean testCircularRedirects() {
    // Circular redirects don't throw an exception with Google Http Client
    return false
  }

  def "error traces when exception is not thrown"() {
    given:
    def uri = resolveAddress("/error")

    when:
    def responseCode = doRequest(method, uri)

    then:
    responseCode == 500
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          kind CLIENT
          status ERROR
          attributes {
            "${SemanticAttributes.NET_TRANSPORT.key}" IP_TCP
            "${SemanticAttributes.NET_PEER_NAME.key}" "localhost"
            "${SemanticAttributes.NET_PEER_PORT.key}" Long
            "${SemanticAttributes.HTTP_URL.key}" "${uri}"
            "${SemanticAttributes.HTTP_METHOD.key}" method
            "${SemanticAttributes.HTTP_STATUS_CODE.key}" 500
            "${SemanticAttributes.HTTP_FLAVOR.key}" "1.1"
          }
        }
        serverSpan(it, 1, span(0))
      }
    }

    where:
    method = "GET"
  }
}
