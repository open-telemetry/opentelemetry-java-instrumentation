/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.v8_0;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.URI;
import java.net.URISyntaxException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.checkerframework.checker.nullness.qual.Nullable;

public class Jetty8AttributesExtractor
    extends HttpAttributesExtractor<HttpServletRequest, HttpServletResponse> {

  @Override
  protected @Nullable String method(HttpServletRequest httpServletRequest) {
    return httpServletRequest.getMethod();
  }

  @Override
  protected @Nullable String url(HttpServletRequest httpServletRequest) {
    URI uri = URI.create(httpServletRequest.getRequestURL().toString());

    try {
      return new URI(
              uri.getScheme(),
              null,
              uri.getHost(),
              uri.getPort(),
              uri.getPath(),
              httpServletRequest.getQueryString(),
              null)
          .toString();
    } catch (URISyntaxException e) {
      return null;
    }
  }

  @Override
  protected @Nullable String target(HttpServletRequest httpServletRequest) {
    return null;
  }

  @Override
  protected @Nullable String host(HttpServletRequest httpServletRequest) {
    return null;
  }

  @Override
  protected @Nullable String route(HttpServletRequest httpServletRequest) {
    return null;
  }

  @Override
  protected @Nullable String scheme(HttpServletRequest httpServletRequest) {
    return null;
  }

  @Override
  protected @Nullable String userAgent(HttpServletRequest httpServletRequest) {
    return httpServletRequest.getHeader("User-Agent");
  }

  @Override
  protected @Nullable Long requestContentLength(
      HttpServletRequest httpServletRequest, @Nullable HttpServletResponse httpServletResponse) {
    return null;
  }

  @Override
  protected @Nullable Long requestContentLengthUncompressed(
      HttpServletRequest httpServletRequest, @Nullable HttpServletResponse httpServletResponse) {
    return null;
  }

  @Override
  protected @Nullable String flavor(
      HttpServletRequest httpServletRequest, @Nullable HttpServletResponse httpServletResponse) {
    String protocol = httpServletRequest.getProtocol();
    switch (protocol) {
      case "HTTP/1.0":
        return SemanticAttributes.HttpFlavorValues.HTTP_1_0;
      case "HTTP/1.1":
        return SemanticAttributes.HttpFlavorValues.HTTP_1_1;
      case "HTTP/2.0":
        return SemanticAttributes.HttpFlavorValues.HTTP_2_0;
      default:
        return null;
    }
  }

  @Override
  protected @Nullable String serverName(
      HttpServletRequest httpServletRequest, @Nullable HttpServletResponse httpServletResponse) {
    return null;
  }

  @Override
  protected @Nullable Integer statusCode(
      HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
    return httpServletResponse.getStatus();
  }

  @Override
  protected @Nullable Long responseContentLength(
      HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
    return null;
  }

  @Override
  protected @Nullable Long responseContentLengthUncompressed(
      HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
    return null;
  }
}
