/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.net.URIAuthority;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ApacheHttpClientHttpAttributesExtractor
    extends HttpClientAttributesExtractor<ClassicHttpRequest, HttpResponse> {

  private static final Logger logger =
      LoggerFactory.getLogger(ApacheHttpClientHttpAttributesExtractor.class);

  @Override
  protected String method(ClassicHttpRequest request) {
    return request.getMethod();
  }

  @Override
  protected String url(ClassicHttpRequest request) {
    // similar to org.apache.hc.core5.http.message.BasicHttpRequest.getUri()
    // not calling getUri() to avoid unnecessary conversion
    StringBuilder url = new StringBuilder();
    URIAuthority authority = request.getAuthority();
    if (authority != null) {
      String scheme = request.getScheme();
      if (scheme != null) {
        url.append(scheme);
        url.append("://");
      } else {
        url.append("http://");
      }
      url.append(authority.getHostName());
      int port = authority.getPort();
      if (port >= 0) {
        url.append(":");
        url.append(port);
      }
    }
    String path = request.getPath();
    if (path != null) {
      if (url.length() > 0 && !path.startsWith("/")) {
        url.append("/");
      }
      url.append(path);
    } else {
      url.append("/");
    }
    return url.toString();
  }

  @Override
  @Nullable
  protected String userAgent(ClassicHttpRequest request) {
    Header header = request.getFirstHeader("User-Agent");
    return header != null ? header.getValue() : null;
  }

  @Override
  @Nullable
  protected Long requestContentLength(ClassicHttpRequest request, @Nullable HttpResponse response) {
    return null;
  }

  @Override
  @Nullable
  protected Long requestContentLengthUncompressed(
      ClassicHttpRequest request, @Nullable HttpResponse response) {
    return null;
  }

  @Override
  protected Integer statusCode(ClassicHttpRequest request, HttpResponse response) {
    return response.getCode();
  }

  @Override
  @Nullable
  protected String flavor(ClassicHttpRequest request, @Nullable HttpResponse response) {
    ProtocolVersion protocolVersion = request.getVersion();
    if (protocolVersion == null) {
      return SemanticAttributes.HttpFlavorValues.HTTP_1_1;
    }
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
  protected Long responseContentLength(ClassicHttpRequest request, HttpResponse response) {
    return null;
  }

  @Override
  @Nullable
  protected Long responseContentLengthUncompressed(
      ClassicHttpRequest request, HttpResponse response) {
    return null;
  }
}
