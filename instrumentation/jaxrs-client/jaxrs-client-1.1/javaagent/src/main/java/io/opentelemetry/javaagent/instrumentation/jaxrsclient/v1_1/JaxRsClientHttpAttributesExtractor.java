/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrsclient.v1_1;

import com.sun.jersey.api.client.ClientRequest;
import com.sun.jersey.api.client.ClientResponse;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.checkerframework.checker.nullness.qual.Nullable;

final class JaxRsClientHttpAttributesExtractor
    extends HttpClientAttributesExtractor<ClientRequest, ClientResponse> {

  @Override
  protected @Nullable String method(ClientRequest httpRequest) {
    return httpRequest.getMethod();
  }

  @Override
  protected String url(ClientRequest httpRequest) {
    return httpRequest.getURI().toString();
  }

  @Override
  protected @Nullable String userAgent(ClientRequest httpRequest) {
    Object header = httpRequest.getHeaders().getFirst("User-Agent");
    return header != null ? header.toString() : null;
  }

  @Override
  protected @Nullable Long requestContentLength(
      ClientRequest httpRequest, @Nullable ClientResponse httpResponse) {
    return null;
  }

  @Override
  protected @Nullable Long requestContentLengthUncompressed(
      ClientRequest httpRequest, @Nullable ClientResponse httpResponse) {
    return null;
  }

  @Override
  protected String flavor(ClientRequest httpRequest, @Nullable ClientResponse httpResponse) {
    return SemanticAttributes.HttpFlavorValues.HTTP_1_1;
  }

  @Override
  protected @Nullable Integer statusCode(ClientRequest httpRequest, ClientResponse httpResponse) {
    return httpResponse.getStatus();
  }

  @Override
  protected @Nullable Long responseContentLength(
      ClientRequest httpRequest, ClientResponse httpResponse) {
    int length = httpResponse.getLength();
    return length != -1 ? (long) length : null;
  }

  @Override
  protected @Nullable Long responseContentLengthUncompressed(
      ClientRequest httpRequest, ClientResponse httpResponse) {
    return null;
  }
}
