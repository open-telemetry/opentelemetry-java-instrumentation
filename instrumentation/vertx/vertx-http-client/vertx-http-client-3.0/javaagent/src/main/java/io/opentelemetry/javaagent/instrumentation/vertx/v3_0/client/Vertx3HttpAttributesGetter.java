/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v3_0.client;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.instrumentation.vertx.client.AbstractVertxHttpAttributesGetter;
import io.vertx.core.http.HttpClientRequest;
import javax.annotation.Nullable;

final class Vertx3HttpAttributesGetter extends AbstractVertxHttpAttributesGetter {

  private static final VirtualField<HttpClientRequest, VertxRequestInfo> requestInfoField =
      VirtualField.find(HttpClientRequest.class, VertxRequestInfo.class);

  @Override
  public String getUrlFull(HttpClientRequest request) {
    String uri = request.uri();
    // Uri should be relative, but it is possible to misuse vert.x api and pass an absolute uri
    // where relative is expected.
    if (!isAbsolute(uri)) {
      VertxRequestInfo requestInfo = requestInfoField.get(request);
      if (requestInfo != null) {
        uri = absoluteUri(requestInfo, uri);
      }
    }
    return uri;
  }

  @Nullable
  @Override
  public String getServerAddress(HttpClientRequest request) {
    VertxRequestInfo requestInfo = requestInfoField.get(request);
    if (requestInfo == null) {
      return null;
    }
    return requestInfo.getHost();
  }

  @Nullable
  @Override
  public Integer getServerPort(HttpClientRequest request) {
    VertxRequestInfo requestInfo = requestInfoField.get(request);
    if (requestInfo == null) {
      return null;
    }
    return requestInfo.getPort();
  }

  private static boolean isAbsolute(String uri) {
    return uri.startsWith("http://") || uri.startsWith("https://");
  }

  private static String absoluteUri(VertxRequestInfo requestInfo, String uri) {
    StringBuilder result = new StringBuilder();
    result.append(requestInfo.isSsl() ? "https://" : "http://");
    result.append(requestInfo.getHost());
    if (requestInfo.getPort() != -1
        && (requestInfo.getPort() != 80 || requestInfo.isSsl())
        && (requestInfo.getPort() != 443 || !requestInfo.isSsl())) {
      result.append(':').append(requestInfo.getPort());
    }
    result.append(uri);
    return result.toString();
  }

  @Override
  public String getHttpRequestMethod(HttpClientRequest request) {
    return request.method().name();
  }
}
