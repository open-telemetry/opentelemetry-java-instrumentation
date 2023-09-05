/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesGetter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.MessageHeaders;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.net.URIAuthority;

final class ApacheHttpClientHttpAttributesGetter
    implements HttpClientAttributesGetter<HttpRequest, HttpResponse> {

  @Override
  public String getHttpRequestMethod(HttpRequest request) {
    return request.getMethod();
  }

  @Override
  public String getUrlFull(HttpRequest request) {
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
  public List<String> getHttpRequestHeader(HttpRequest request, String name) {
    return getHeader(request, name);
  }

  @Override
  public Integer getHttpResponseStatusCode(
      HttpRequest request, HttpResponse response, @Nullable Throwable error) {
    return response.getCode();
  }

  @Override
  public List<String> getHttpResponseHeader(
      HttpRequest request, HttpResponse response, String name) {
    return getHeader(response, name);
  }

  private static List<String> getHeader(MessageHeaders messageHeaders, String name) {
    return headersToList(messageHeaders.getHeaders(name));
  }

  // minimize memory overhead by not using streams
  private static List<String> headersToList(Header[] headers) {
    if (headers.length == 0) {
      return Collections.emptyList();
    }
    List<String> headersList = new ArrayList<>(headers.length);
    for (Header header : headers) {
      headersList.add(header.getValue());
    }
    return headersList;
  }

  @Nullable
  @Override
  public String getNetworkProtocolName(HttpRequest request, @Nullable HttpResponse response) {
    ProtocolVersion protocolVersion = getVersion(request, response);
    if (protocolVersion == null) {
      return null;
    }
    return protocolVersion.getProtocol();
  }

  @Nullable
  @Override
  public String getNetworkProtocolVersion(HttpRequest request, @Nullable HttpResponse response) {
    ProtocolVersion protocolVersion = getVersion(request, response);
    if (protocolVersion == null) {
      return null;
    }
    if (protocolVersion.getMinor() == 0) {
      return Integer.toString(protocolVersion.getMajor());
    }
    return protocolVersion.getMajor() + "." + protocolVersion.getMinor();
  }

  @Override
  @Nullable
  public String getServerAddress(HttpRequest request) {
    return request.getAuthority().getHostName();
  }

  @Override
  public Integer getServerPort(HttpRequest request) {
    return request.getAuthority().getPort();
  }

  private static ProtocolVersion getVersion(HttpRequest request, @Nullable HttpResponse response) {
    ProtocolVersion protocolVersion = request.getVersion();
    if (protocolVersion == null && response != null) {
      protocolVersion = response.getVersion();
    }
    return protocolVersion;
  }
}
