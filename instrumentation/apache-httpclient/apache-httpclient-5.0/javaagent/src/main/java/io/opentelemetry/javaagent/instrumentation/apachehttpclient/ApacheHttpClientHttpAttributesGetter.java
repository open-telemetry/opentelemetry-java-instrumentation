/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesGetter;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.net.URIAuthority;

final class ApacheHttpClientHttpAttributesGetter
    implements HttpClientAttributesGetter<HttpRequest, HttpResponse> {

  @Override
  public String method(HttpRequest request) {
    return request.getMethod();
  }

  @Override
  public String url(HttpRequest request) {
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
  public List<String> requestHeader(HttpRequest request, String name) {
    return HttpUtils.headersToList(request.getHeaders(name));
  }

  @Override
  @Nullable
  public Long requestContentLength(HttpRequest request, @Nullable HttpResponse response) {
    return null;
  }

  @Override
  @Nullable
  public Long requestContentLengthUncompressed(
      HttpRequest request, @Nullable HttpResponse response) {
    return null;
  }

  @Override
  public Integer statusCode(HttpRequest request, HttpResponse response) {
    return response.getCode();
  }

  @Override
  @Nullable
  public String flavor(HttpRequest request, @Nullable HttpResponse response) {
    return HttpUtils.getFlavor(request, response);
  }

  @Override
  @Nullable
  public Long responseContentLength(HttpRequest request, HttpResponse response) {
    return null;
  }

  @Override
  @Nullable
  public Long responseContentLengthUncompressed(HttpRequest request, HttpResponse response) {
    return null;
  }

  @Override
  public List<String> responseHeader(HttpRequest request, HttpResponse response, String name) {
    return HttpUtils.headersToList(response.getHeaders(name));
  }
}
