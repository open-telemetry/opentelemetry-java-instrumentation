/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrsclient.v2_0;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientResponseContext;
import org.checkerframework.checker.nullness.qual.Nullable;

final class JaxRsClientHttpAttributesExtractor
    extends HttpClientAttributesExtractor<ClientRequestContext, ClientResponseContext> {

  @Override
  protected @Nullable String method(ClientRequestContext httpRequest) {
    return httpRequest.getMethod();
  }

  @Override
  protected String url(ClientRequestContext httpRequest) {
    return httpRequest.getUri().toString();
  }

  @Override
  protected @Nullable String userAgent(ClientRequestContext httpRequest) {
    return httpRequest.getHeaderString("User-Agent");
  }

  @Override
  protected @Nullable Long requestContentLength(
      ClientRequestContext httpRequest, @Nullable ClientResponseContext httpResponse) {
    return null;
  }

  @Override
  protected @Nullable Long requestContentLengthUncompressed(
      ClientRequestContext httpRequest, @Nullable ClientResponseContext httpResponse) {
    return null;
  }

  @Override
  protected String flavor(
      ClientRequestContext httpRequest, @Nullable ClientResponseContext httpResponse) {
    return SemanticAttributes.HttpFlavorValues.HTTP_1_1;
  }

  @Override
  protected @Nullable Integer statusCode(
      ClientRequestContext httpRequest, ClientResponseContext httpResponse) {
    return httpResponse.getStatus();
  }

  @Override
  protected @Nullable Long responseContentLength(
      ClientRequestContext httpRequest, ClientResponseContext httpResponse) {
    int length = httpResponse.getLength();
    return length != -1 ? (long) length : null;
  }

  @Override
  protected @Nullable Long responseContentLengthUncompressed(
      ClientRequestContext httpRequest, ClientResponseContext httpResponse) {
    return null;
  }
}
