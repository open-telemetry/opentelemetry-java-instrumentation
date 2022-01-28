/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrsclient.v2_0;

import static java.util.Collections.emptyList;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import javax.ws.rs.core.Response;
import org.jboss.resteasy.client.jaxrs.internal.ClientInvocation;

final class ResteasyClientHttpAttributesGetter
    implements HttpClientAttributesGetter<ClientInvocation, Response> {

  @Override
  @Nullable
  public String method(ClientInvocation httpRequest) {
    return httpRequest.getMethod();
  }

  @Override
  public String url(ClientInvocation httpRequest) {
    return httpRequest.getUri().toString();
  }

  @Override
  public List<String> requestHeader(ClientInvocation httpRequest, String name) {
    List<Object> rawHeaders = httpRequest.getHeaders().getHeaders().getOrDefault(name, emptyList());
    if (rawHeaders.isEmpty()) {
      return emptyList();
    }
    List<String> stringHeaders = new ArrayList<>(rawHeaders.size());
    for (Object headerValue : rawHeaders) {
      stringHeaders.add(String.valueOf(headerValue));
    }
    return stringHeaders;
  }

  @Override
  @Nullable
  public Long requestContentLength(ClientInvocation httpRequest, @Nullable Response httpResponse) {
    return null;
  }

  @Override
  @Nullable
  public Long requestContentLengthUncompressed(
      ClientInvocation httpRequest, @Nullable Response httpResponse) {
    return null;
  }

  @Override
  public String flavor(ClientInvocation httpRequest, @Nullable Response httpResponse) {
    return SemanticAttributes.HttpFlavorValues.HTTP_1_1;
  }

  @Override
  public Integer statusCode(ClientInvocation httpRequest, Response httpResponse) {
    return httpResponse.getStatus();
  }

  @Override
  @Nullable
  public Long responseContentLength(ClientInvocation httpRequest, Response httpResponse) {
    int length = httpResponse.getLength();
    return length != -1 ? (long) length : null;
  }

  @Override
  @Nullable
  public Long responseContentLengthUncompressed(
      ClientInvocation httpRequest, Response httpResponse) {
    return null;
  }

  @Override
  public List<String> responseHeader(
      ClientInvocation clientInvocation, Response response, String name) {
    return response.getStringHeaders().getOrDefault(name, emptyList());
  }
}
