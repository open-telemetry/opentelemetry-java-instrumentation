/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.URI;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.methods.HttpUriRequest;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ApacheHttpClientHttpAttributesExtractor
    extends HttpAttributesExtractor<HttpUriRequest, HttpResponse> {

  private static final Logger logger =
      LoggerFactory.getLogger(ApacheHttpClientHttpAttributesExtractor.class);

  @Override
  protected String method(HttpUriRequest request) {
    return request.getMethod();
  }

  @Override
  protected String url(HttpUriRequest request) {
    return request.getURI().toString();
  }

  @Override
  protected String target(HttpUriRequest request) {
    URI uri = request.getURI();
    String pathString = uri.getPath();
    String queryString = uri.getQuery();
    if (pathString != null && queryString != null) {
      return pathString + "?" + queryString;
    } else if (queryString != null) {
      return "?" + queryString;
    } else {
      return pathString;
    }
  }

  @Override
  @Nullable
  protected String host(HttpUriRequest request) {
    Header header = request.getFirstHeader("Host");
    if (header != null) {
      return header.getValue();
    }
    return null;
  }

  @Override
  @Nullable
  protected String scheme(HttpUriRequest request) {
    return request.getURI().getScheme();
  }

  @Override
  @Nullable
  protected String userAgent(HttpUriRequest request) {
    Header header = request.getFirstHeader("User-Agent");
    return header != null ? header.getValue() : null;
  }

  @Override
  @Nullable
  protected Long requestContentLength(HttpUriRequest request, @Nullable HttpResponse response) {
    return null;
  }

  @Override
  @Nullable
  protected Long requestContentLengthUncompressed(
      HttpUriRequest request, @Nullable HttpResponse response) {
    return null;
  }

  @Override
  protected Integer statusCode(HttpUriRequest request, HttpResponse response) {
    return response.getStatusLine().getStatusCode();
  }

  @Override
  @Nullable
  protected String flavor(HttpUriRequest request, @Nullable HttpResponse response) {
    ProtocolVersion protocolVersion = request.getRequestLine().getProtocolVersion();
    String protocol = protocolVersion.getProtocol();
    if (!protocol.equals("HTTP")) {
      return null;
    }
    int major = protocolVersion.getMajor();
    int minor = protocolVersion.getMinor();
    if (major == 1 && minor == 0) {
      return SemanticAttributes.HttpFlavorValues.HTTP_1_0;
    }
    if (major == 1 && minor == 1) {
      return SemanticAttributes.HttpFlavorValues.HTTP_1_1;
    }
    if (major == 2 && minor == 0) {
      return SemanticAttributes.HttpFlavorValues.HTTP_2_0;
    }
    logger.debug("unexpected http protocol version: " + protocolVersion);
    return null;
  }

  @Override
  @Nullable
  protected Long responseContentLength(HttpUriRequest request, HttpResponse response) {
    return null;
  }

  @Override
  @Nullable
  protected Long responseContentLengthUncompressed(HttpUriRequest request, HttpResponse response) {
    return null;
  }

  @Override
  @Nullable
  protected String serverName(HttpUriRequest request, @Nullable HttpResponse response) {
    return null;
  }

  @Override
  @Nullable
  protected String route(HttpUriRequest request) {
    return null;
  }

  @Override
  @Nullable
  protected String clientIp(HttpUriRequest request, @Nullable HttpResponse response) {
    return null;
  }
}
