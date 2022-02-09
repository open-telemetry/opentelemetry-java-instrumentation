/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrsclient.v2_0;

import static java.util.Collections.emptyList;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.List;
import javax.annotation.Nullable;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientResponseContext;

final class JaxRsClientHttpAttributesGetter
    implements HttpClientAttributesGetter<ClientRequestContext, ClientResponseContext> {

  @Override
  @Nullable
  public String method(ClientRequestContext httpRequest) {
    return httpRequest.getMethod();
  }

  @Override
  public String url(ClientRequestContext httpRequest) {
    return httpRequest.getUri().toString();
  }

  @Override
  public List<String> requestHeader(ClientRequestContext httpRequest, String name) {
    return httpRequest.getStringHeaders().getOrDefault(name, emptyList());
  }

  @Override
  @Nullable
  public Long requestContentLength(
      ClientRequestContext httpRequest, @Nullable ClientResponseContext httpResponse) {
    return null;
  }

  @Override
  @Nullable
  public Long requestContentLengthUncompressed(
      ClientRequestContext httpRequest, @Nullable ClientResponseContext httpResponse) {
    return null;
  }

  @Override
  public String flavor(
      ClientRequestContext httpRequest, @Nullable ClientResponseContext httpResponse) {
    return SemanticAttributes.HttpFlavorValues.HTTP_1_1;
  }

  @Override
  public Integer statusCode(ClientRequestContext httpRequest, ClientResponseContext httpResponse) {
    return httpResponse.getStatus();
  }

  @Override
  @Nullable
  public Long responseContentLength(
      ClientRequestContext httpRequest, ClientResponseContext httpResponse) {
    int length = httpResponse.getLength();
    return length != -1 ? (long) length : null;
  }

  @Override
  @Nullable
  public Long responseContentLengthUncompressed(
      ClientRequestContext httpRequest, ClientResponseContext httpResponse) {
    return null;
  }

  @Override
  public List<String> responseHeader(
      ClientRequestContext httpRequest, ClientResponseContext httpResponse, String name) {
    return httpResponse.getHeaders().getOrDefault(name, emptyList());
  }
}
