/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8.server;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesGetter;
import io.opentelemetry.javaagent.instrumentation.netty.v3_8.HttpRequestAndChannel;
import io.opentelemetry.javaagent.instrumentation.netty.v3_8.util.HttpSchemeUtil;
import java.util.List;
import javax.annotation.Nullable;
import org.jboss.netty.handler.codec.http.HttpResponse;

final class NettyHttpServerAttributesGetter
    implements HttpServerAttributesGetter<HttpRequestAndChannel, HttpResponse> {

  @Override
  public String getHttpRequestMethod(HttpRequestAndChannel requestAndChannel) {
    return requestAndChannel.request().getMethod().getName();
  }

  @Override
  public List<String> getHttpRequestHeader(HttpRequestAndChannel requestAndChannel, String name) {
    return requestAndChannel.request().headers().getAll(name);
  }

  @Override
  public Integer getHttpResponseStatusCode(
      HttpRequestAndChannel requestAndChannel, HttpResponse response, @Nullable Throwable error) {
    return response.getStatus().getCode();
  }

  @Override
  public List<String> getHttpResponseHeader(
      HttpRequestAndChannel requestAndChannel, HttpResponse response, String name) {
    return response.headers().getAll(name);
  }

  @Override
  public String getUrlScheme(HttpRequestAndChannel requestAndChannel) {
    return HttpSchemeUtil.getScheme(requestAndChannel);
  }

  @Override
  public String getUrlPath(HttpRequestAndChannel requestAndChannel) {
    String fullPath = requestAndChannel.request().getUri();
    int separatorPos = fullPath.indexOf('?');
    return separatorPos == -1 ? fullPath : fullPath.substring(0, separatorPos);
  }

  @Override
  public String getUrlQuery(HttpRequestAndChannel requestAndChannel) {
    String fullPath = requestAndChannel.request().getUri();
    int separatorPos = fullPath.indexOf('?');
    return separatorPos == -1 ? null : fullPath.substring(separatorPos + 1);
  }
}
