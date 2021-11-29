/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v3_0.client;

import io.opentelemetry.instrumentation.api.field.VirtualField;
import io.opentelemetry.javaagent.instrumentation.vertx.client.AbstractVertxHttpAttributesExtractor;
import io.vertx.core.http.HttpClientRequest;
import javax.annotation.Nullable;

final class Vertx3HttpAttributesExtractor extends AbstractVertxHttpAttributesExtractor {
  private static final VirtualField requestInfoField =
      VirtualField.find(HttpClientRequest.class, VertxRequestInfo.class);

  @Nullable
  @Override
  protected String url(HttpClientRequest request) {
    String uri = request.uri();
    // Uri should be relative, but it is possible to misuse vert.x api and pass an absolute uri
    // where relative is expected.
    if (!isAbsolute(uri)) {
      VertxRequestInfo requestInfo = (VertxRequestInfo) requestInfoField.get(request);
      uri = absoluteUri(requestInfo, uri);
    }
    return uri;
  }

  private static boolean isAbsolute(String uri) {
    return uri.startsWith("http://") || uri.startsWith("https://");
  }

  private static String absoluteUri(VertxRequestInfo requestInfo, String uri) {
    String result = requestInfo.isSsl() ? "https://" : "http://";
    result += requestInfo.getHost();
    if (requestInfo.getPort() != -1
        && (requestInfo.getPort() != 80 || requestInfo.isSsl())
        && (requestInfo.getPort() != 443 || !requestInfo.isSsl())) {
      result += ":" + requestInfo.getPort();
    }
    result += uri;
    return result;
  }

  @Override
  protected String method(HttpClientRequest request) {
    return request.method().name();
  }
}
