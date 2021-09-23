/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrsclient.v2_0;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
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
  protected @Nullable String target(ClientInvocation httpRequest) {
    StringBuilder result = new StringBuilder();
    String path = httpRequest.getUri().getPath();
    if (path != null) {
      result.append(path);
    }
    String query = httpRequest.getUri().getQuery();
    if (query != null) {
      result.append('?');
      result.append(query);
    }
    String fragment = httpRequest.getUri().getFragment();
    if (fragment != null) {
      result.append('#');
      result.append(fragment);
    }
    return result.length() > 0 ? result.toString() : null;
  }

  @Override
  protected @Nullable String host(ClientInvocation httpRequest) {
    return httpRequest.getUri().getHost();
  }

  @Override
  protected @Nullable String scheme(ClientInvocation httpRequest) {
    return httpRequest.getUri().getScheme();
  }

  @Override
  protected @Nullable String userAgent(ClientInvocation httpRequest) {
    return httpRequest.getHeaders().getHeader("User-Agent");
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
}
