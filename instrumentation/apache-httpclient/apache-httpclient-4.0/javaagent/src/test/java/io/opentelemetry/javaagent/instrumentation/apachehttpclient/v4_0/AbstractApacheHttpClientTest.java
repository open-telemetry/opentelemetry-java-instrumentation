/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.URI;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;

abstract class AbstractApacheHttpClientTest<T extends HttpRequest>
    extends AbstractHttpClientTest<T> {
  @Override
  protected String userAgent() {
    return "apachehttpclient";
  }

  @Override
  protected void configure(HttpClientTestOptions options) {
    options.setUserAgent(userAgent());
    options.enableTestReadTimeout();
    options.setHttpAttributes(this::getHttpAttributes);
  }

  protected Set<AttributeKey<?>> getHttpAttributes(URI endpoint) {
    Set<AttributeKey<?>> attributes = new HashSet<>();
    attributes.add(SemanticAttributes.NET_PEER_NAME);
    attributes.add(SemanticAttributes.NET_PEER_PORT);
    attributes.add(SemanticAttributes.HTTP_URL);
    attributes.add(SemanticAttributes.HTTP_METHOD);
    if (endpoint.toString().contains("/success")) {
      attributes.add(SemanticAttributes.HTTP_FLAVOR);
    }
    attributes.add(SemanticAttributes.HTTP_USER_AGENT);
    return attributes;
  }

  @Override
  protected T buildRequest(String method, URI uri, Map<String, String> headers) {
    T request = createRequest(method, uri);
    request.addHeader("user-agent", userAgent());
    headers.forEach(request::setHeader);
    return request;
  }

  @Override
  protected int sendRequest(T request, String method, URI uri, Map<String, String> headers)
      throws Exception {
    return getResponseCode(executeRequest(request, uri));
  }

  @Override
  protected void sendRequestWithCallback(
      T request, String method, URI uri, Map<String, String> headers, RequestResult requestResult) {
    try {
      executeRequestWithCallback(request, uri, requestResult);
    } catch (Throwable throwable) {
      requestResult.complete(throwable);
    }
  }

  protected HttpHost getHost(URI uri) {
    return new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme());
  }

  protected HttpContext getContext() {
    return new BasicHttpContext();
  }

  protected static String fullPathFromUri(URI uri) {
    StringBuilder builder = new StringBuilder();
    if (uri.getPath() != null) {
      builder.append(uri.getPath());
    }

    if (uri.getQuery() != null) {
      builder.append('?');
      builder.append(uri.getQuery());
    }

    if (uri.getFragment() != null) {
      builder.append('#');
      builder.append(uri.getFragment());
    }
    return builder.toString();
  }

  abstract T createRequest(String method, URI uri);

  abstract HttpResponse executeRequest(T request, URI uri) throws Exception;

  abstract void executeRequestWithCallback(T request, URI uri, RequestResult requestResult)
      throws Exception;

  private static int getResponseCode(HttpResponse response) {
    return response.getStatusLine().getStatusCode();
  }
}
