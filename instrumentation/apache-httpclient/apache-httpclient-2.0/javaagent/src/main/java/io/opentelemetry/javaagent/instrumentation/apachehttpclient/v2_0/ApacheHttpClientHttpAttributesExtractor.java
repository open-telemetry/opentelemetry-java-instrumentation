/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v2_0;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpMethodBase;
import org.apache.commons.httpclient.StatusLine;
import org.checkerframework.checker.nullness.qual.Nullable;

final class ApacheHttpClientHttpAttributesExtractor
    extends HttpAttributesExtractor<HttpMethod, HttpMethod> {

  @Override
  protected String method(HttpMethod request) {
    return request.getName();
  }

  @Override
  protected String url(HttpMethod request) {
    return getUrl(request);
  }

  @Override
  protected String target(HttpMethod request) {
    String queryString = request.getQueryString();
    return queryString != null ? request.getPath() + "?" + queryString : request.getPath();
  }

  @Override
  @Nullable
  protected String host(HttpMethod request) {
    Header header = request.getRequestHeader("Host");
    if (header != null) {
      return header.getValue();
    }
    HostConfiguration hostConfiguration = request.getHostConfiguration();
    if (hostConfiguration != null) {
      return hostConfiguration.getVirtualHost();
    }
    return null;
  }

  @Override
  @Nullable
  protected String scheme(HttpMethod request) {
    HostConfiguration hostConfiguration = request.getHostConfiguration();
    return hostConfiguration != null ? hostConfiguration.getProtocol().getScheme() : null;
  }

  @Override
  @Nullable
  protected String userAgent(HttpMethod request) {
    Header header = request.getRequestHeader("User-Agent");
    return header != null ? header.getValue() : null;
  }

  @Override
  @Nullable
  protected Long requestContentLength(HttpMethod request, @Nullable HttpMethod response) {
    return null;
  }

  @Override
  @Nullable
  protected Long requestContentLengthUncompressed(
      HttpMethod request, @Nullable HttpMethod response) {
    return null;
  }

  @Override
  @Nullable
  protected Integer statusCode(HttpMethod request, HttpMethod response) {
    StatusLine statusLine = response.getStatusLine();
    return statusLine == null ? null : statusLine.getStatusCode();
  }

  @Override
  @Nullable
  protected String flavor(HttpMethod request, @Nullable HttpMethod response) {
    if (request instanceof HttpMethodBase) {
      return ((HttpMethodBase) request).isHttp11()
          ? SemanticAttributes.HttpFlavorValues.HTTP_1_1
          : SemanticAttributes.HttpFlavorValues.HTTP_1_0;
    }
    return null;
  }

  @Override
  @Nullable
  protected Long responseContentLength(HttpMethod request, HttpMethod response) {
    return null;
  }

  @Override
  @Nullable
  protected Long responseContentLengthUncompressed(HttpMethod request, HttpMethod response) {
    return null;
  }

  @Override
  @Nullable
  protected String serverName(HttpMethod request, @Nullable HttpMethod response) {
    return null;
  }

  @Override
  @Nullable
  protected String route(HttpMethod request) {
    return null;
  }

  // mirroring implementation HttpMethodBase.getURI(), to avoid converting to URI and back to String
  private static String getUrl(HttpMethod request) {
    HostConfiguration hostConfiguration = request.getHostConfiguration();
    if (hostConfiguration == null) {
      String queryString = request.getQueryString();
      if (queryString == null) {
        return request.getPath();
      } else {
        return request.getPath() + "?" + request.getQueryString();
      }
    } else {
      StringBuilder url = new StringBuilder();
      url.append(hostConfiguration.getProtocol().getScheme());
      url.append("://");
      url.append(hostConfiguration.getHost());
      int port = hostConfiguration.getPort();
      if (port != hostConfiguration.getProtocol().getDefaultPort()) {
        url.append(":");
        url.append(port);
      }
      url.append(request.getPath());
      String queryString = request.getQueryString();
      if (queryString != null) {
        url.append("?");
        url.append(request.getQueryString());
      }
      return url.toString();
    }
  }
}
