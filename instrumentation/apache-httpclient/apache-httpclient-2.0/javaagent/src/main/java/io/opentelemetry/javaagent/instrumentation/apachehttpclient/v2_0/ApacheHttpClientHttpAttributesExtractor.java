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
  protected String method(HttpMethod httpMethod) {
    return httpMethod.getName();
  }

  @Override
  protected String url(HttpMethod httpMethod) {
    return getUrl(httpMethod);
  }

  @Override
  protected String target(HttpMethod httpMethod) {
    String queryString = httpMethod.getQueryString();
    return queryString != null ? httpMethod.getPath() + "?" + queryString : httpMethod.getPath();
  }

  @Override
  @Nullable
  protected String host(HttpMethod httpMethod) {
    Header header = httpMethod.getRequestHeader("Host");
    if (header != null) {
      return header.getValue();
    }
    HostConfiguration hostConfiguration = httpMethod.getHostConfiguration();
    if (hostConfiguration != null) {
      return hostConfiguration.getVirtualHost();
    }
    return null;
  }

  @Override
  @Nullable
  protected String scheme(HttpMethod httpMethod) {
    HostConfiguration hostConfiguration = httpMethod.getHostConfiguration();
    return hostConfiguration != null ? hostConfiguration.getProtocol().getScheme() : null;
  }

  @Override
  @Nullable
  protected String userAgent(HttpMethod httpMethod) {
    Header header = httpMethod.getRequestHeader("User-Agent");
    return header != null ? header.getValue() : null;
  }

  @Override
  @Nullable
  protected Long requestContentLength(HttpMethod httpMethod, @Nullable HttpMethod response) {
    return null;
  }

  @Override
  @Nullable
  protected Long requestContentLengthUncompressed(
      HttpMethod httpMethod, @Nullable HttpMethod response) {
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
  protected String flavor(HttpMethod httpMethod, @Nullable HttpMethod response) {
    if (httpMethod instanceof HttpMethodBase) {
      return ((HttpMethodBase) httpMethod).isHttp11()
          ? SemanticAttributes.HttpFlavorValues.HTTP_1_1
          : SemanticAttributes.HttpFlavorValues.HTTP_1_0;
    }
    return null;
  }

  @Override
  @Nullable
  protected Long responseContentLength(HttpMethod httpMethod, HttpMethod response) {
    return null;
  }

  @Override
  @Nullable
  protected Long responseContentLengthUncompressed(HttpMethod httpMethod, HttpMethod response) {
    return null;
  }

  @Override
  @Nullable
  protected String serverName(HttpMethod httpMethod, @Nullable HttpMethod response) {
    return null;
  }

  @Override
  @Nullable
  protected String route(HttpMethod httpMethod) {
    return null;
  }

  @Override
  @Nullable
  protected String clientIp(HttpMethod httpMethod, @Nullable HttpMethod response) {
    return null;
  }

  // mirroring implementation HttpMethodBase.getURI(), to avoid converting to URI and back to String
  private static String getUrl(HttpMethod httpMethod) {
    HostConfiguration hostConfiguration = httpMethod.getHostConfiguration();
    if (hostConfiguration == null) {
      String queryString = httpMethod.getQueryString();
      if (queryString == null) {
        return httpMethod.getPath();
      } else {
        return httpMethod.getPath() + "?" + httpMethod.getQueryString();
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
      url.append(httpMethod.getPath());
      String queryString = httpMethod.getQueryString();
      if (queryString != null) {
        url.append("?");
        url.append(httpMethod.getQueryString());
      }
      return url.toString();
    }
  }
}
