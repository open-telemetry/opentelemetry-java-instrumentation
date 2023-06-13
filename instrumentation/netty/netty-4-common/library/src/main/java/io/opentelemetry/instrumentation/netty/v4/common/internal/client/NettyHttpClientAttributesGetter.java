/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.v4.common.internal.client;

import static io.opentelemetry.instrumentation.netty.v4.common.internal.HttpSchemeUtil.getScheme;

import io.netty.handler.codec.http.HttpResponse;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesGetter;
import io.opentelemetry.instrumentation.netty.v4.common.HttpRequestAndChannel;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import javax.annotation.Nullable;

final class NettyHttpClientAttributesGetter
    implements HttpClientAttributesGetter<HttpRequestAndChannel, HttpResponse> {

  @Override
  @Nullable
  public String getUrlFull(HttpRequestAndChannel requestAndChannel) {
    try {
      String hostHeader = getHost(requestAndChannel);
      String target = requestAndChannel.request().getUri();
      URI uri = new URI(target);
      if ((uri.getHost() == null || uri.getHost().equals("")) && hostHeader != null) {
        return getScheme(requestAndChannel) + "://" + hostHeader + target;
      }
      return uri.toString();
    } catch (URISyntaxException e) {
      return null;
    }
  }

  private String getHost(HttpRequestAndChannel requestAndChannel) {
    List<String> values = getHttpRequestHeader(requestAndChannel, "host");
    return values.isEmpty() ? null : values.get(0);
  }

  @Override
  public String getHttpRequestMethod(HttpRequestAndChannel requestAndChannel) {
    return requestAndChannel.request().getMethod().name();
  }

  @Override
  public List<String> getHttpRequestHeader(HttpRequestAndChannel requestAndChannel, String name) {
    return requestAndChannel.request().headers().getAll(name);
  }

  @Override
  public Integer getHttpResponseStatusCode(
      HttpRequestAndChannel requestAndChannel, HttpResponse response, @Nullable Throwable error) {
    return response.getStatus().code();
  }

  @Override
  public List<String> getHttpResponseHeader(
      HttpRequestAndChannel requestAndChannel, HttpResponse response, String name) {
    return response.headers().getAll(name);
  }
}
