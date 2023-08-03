/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v2_0;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesGetter;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpMethodBase;
import org.apache.commons.httpclient.StatusLine;

final class ApacheHttpClientHttpAttributesGetter
    implements HttpClientAttributesGetter<HttpMethod, HttpMethod> {

  @Override
  public String getHttpRequestMethod(HttpMethod request) {
    return request.getName();
  }

  // mirroring implementation HttpMethodBase.getURI(), to avoid converting to URI and back to String
  @Override
  public String getUrlFull(HttpMethod request) {
    HostConfiguration hostConfiguration = request.getHostConfiguration();
    if (hostConfiguration == null || hostConfiguration.getProtocol() == null) {
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

  @Override
  public List<String> getHttpRequestHeader(HttpMethod request, String name) {
    Header header = request.getRequestHeader(name);
    return header == null ? emptyList() : singletonList(header.getValue());
  }

  @Override
  @Nullable
  public Integer getHttpResponseStatusCode(
      HttpMethod request, HttpMethod response, @Nullable Throwable error) {
    StatusLine statusLine = response.getStatusLine();
    return statusLine == null ? null : statusLine.getStatusCode();
  }

  @Override
  public List<String> getHttpResponseHeader(HttpMethod request, HttpMethod response, String name) {
    Header header = response.getResponseHeader(name);
    return header == null ? emptyList() : singletonList(header.getValue());
  }

  @Override
  public String getNetworkProtocolName(HttpMethod request, @Nullable HttpMethod response) {
    return "http";
  }

  @Nullable
  @Override
  public String getNetworkProtocolVersion(HttpMethod request, @Nullable HttpMethod response) {
    if (request instanceof HttpMethodBase) {
      return ((HttpMethodBase) request).isHttp11() ? "1.1" : "1.0";
    }
    return null;
  }

  @Override
  @Nullable
  public String getServerAddress(HttpMethod request) {
    HostConfiguration hostConfiguration = request.getHostConfiguration();
    return hostConfiguration != null ? hostConfiguration.getHost() : null;
  }

  @Override
  @Nullable
  public Integer getServerPort(HttpMethod request) {
    HostConfiguration hostConfiguration = request.getHostConfiguration();
    return hostConfiguration != null ? hostConfiguration.getPort() : null;
  }
}
