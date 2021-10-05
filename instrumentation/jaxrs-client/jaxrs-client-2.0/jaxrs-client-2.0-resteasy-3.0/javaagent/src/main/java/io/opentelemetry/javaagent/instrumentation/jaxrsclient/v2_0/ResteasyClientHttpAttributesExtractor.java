/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrsclient.v2_0;

import static java.util.Collections.emptyList;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.ArrayList;
import java.util.List;
import javax.ws.rs.core.Response;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jboss.resteasy.client.jaxrs.internal.ClientInvocation;

final class ResteasyClientHttpAttributesExtractor
    extends HttpClientAttributesExtractor<ClientInvocation, Response> {

  @Override
  protected @Nullable String method(ClientInvocation httpRequest) {
    return httpRequest.getMethod();
  }

  @Override
  protected String url(ClientInvocation httpRequest) {
    return httpRequest.getUri().toString();
  }

  @Override
  protected @Nullable String userAgent(ClientInvocation httpRequest) {
    return httpRequest.getHeaders().getHeader("User-Agent");
  }

  @Override
  protected List<String> requestHeader(ClientInvocation httpRequest, String name) {
    List<Object> rawHeaders = httpRequest.getHeaders().getHeaders().getOrDefault(name, emptyList());
    if (rawHeaders.isEmpty()) {
      return emptyList();
    }
    List<String> stringHeaders = new ArrayList<>(rawHeaders.size());
    int i = 0;
    for (Object headerValue : rawHeaders) {
      stringHeaders.set(i++, String.valueOf(headerValue));
    }
    return stringHeaders;
  }

  @Override
  protected @Nullable Long requestContentLength(
      ClientInvocation httpRequest, @Nullable Response httpResponse) {
    return null;
  }

  @Override
  protected @Nullable Long requestContentLengthUncompressed(
      ClientInvocation httpRequest, @Nullable Response httpResponse) {
    return null;
  }

  @Override
  protected String flavor(ClientInvocation httpRequest, @Nullable Response httpResponse) {
    return SemanticAttributes.HttpFlavorValues.HTTP_1_1;
  }

  @Override
  protected @Nullable Integer statusCode(ClientInvocation httpRequest, Response httpResponse) {
    return httpResponse.getStatus();
  }

  @Override
  protected @Nullable Long responseContentLength(
      ClientInvocation httpRequest, Response httpResponse) {
    int length = httpResponse.getLength();
    return length != -1 ? (long) length : null;
  }

  @Override
  protected @Nullable Long responseContentLengthUncompressed(
      ClientInvocation httpRequest, Response httpResponse) {
    return null;
  }

  @Override
  protected List<String> responseHeader(
      ClientInvocation clientInvocation, Response response, String name) {
    return response.getStringHeaders().getOrDefault(name, emptyList());
  }
}
