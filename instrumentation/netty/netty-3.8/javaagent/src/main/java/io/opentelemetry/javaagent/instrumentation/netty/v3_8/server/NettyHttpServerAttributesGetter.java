/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8.server;

import static io.opentelemetry.javaagent.instrumentation.netty.v3_8.util.HttpSchemeUtil.getScheme;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesGetter;
import io.opentelemetry.javaagent.instrumentation.netty.v3_8.HttpRequestAndChannel;
import java.util.List;
import javax.annotation.Nullable;
import org.jboss.netty.handler.codec.http.HttpResponse;

final class NettyHttpServerAttributesGetter
    implements HttpServerAttributesGetter<HttpRequestAndChannel, HttpResponse> {

  @Override
  public String method(HttpRequestAndChannel requestAndChannel) {
    return requestAndChannel.request().getMethod().getName();
  }

  @Override
  public List<String> requestHeader(HttpRequestAndChannel requestAndChannel, String name) {
    return requestAndChannel.request().headers().getAll(name);
  }

  @Override
  @Nullable
  public Long requestContentLength(
      HttpRequestAndChannel requestAndChannel, @Nullable HttpResponse response) {
    return null;
  }

  @Override
  @Nullable
  public Long requestContentLengthUncompressed(
      HttpRequestAndChannel requestAndChannel, @Nullable HttpResponse response) {
    return null;
  }

  @Override
  public Integer statusCode(HttpRequestAndChannel requestAndChannel, HttpResponse response) {
    return response.getStatus().getCode();
  }

  @Override
  @Nullable
  public Long responseContentLength(
      HttpRequestAndChannel requestAndChannel, HttpResponse response) {
    return null;
  }

  @Override
  @Nullable
  public Long responseContentLengthUncompressed(
      HttpRequestAndChannel requestAndChannel, HttpResponse response) {
    return null;
  }

  @Override
  public List<String> responseHeader(
      HttpRequestAndChannel requestAndChannel, HttpResponse response, String name) {
    return response.headers().getAll(name);
  }

  @Override
  public String flavor(HttpRequestAndChannel requestAndChannel) {
    String flavor = requestAndChannel.request().getProtocolVersion().toString();
    if (flavor.startsWith("HTTP/")) {
      flavor = flavor.substring("HTTP/".length());
    }
    return flavor;
  }

  @Override
  public String target(HttpRequestAndChannel requestAndChannel) {
    return requestAndChannel.request().getUri();
  }

  @Override
  @Nullable
  public String route(HttpRequestAndChannel requestAndChannel) {
    return null;
  }

  @Override
  public String scheme(HttpRequestAndChannel requestAndChannel) {
    return getScheme(requestAndChannel);
  }

  @Override
  @Nullable
  public String serverName(HttpRequestAndChannel requestAndChannel) {
    return null;
  }
}
