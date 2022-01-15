/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.common.server;

import static io.opentelemetry.javaagent.instrumentation.netty.common.HttpSchemeUtil.getScheme;

import io.netty.handler.codec.http.HttpResponse;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesExtractor;
import io.opentelemetry.javaagent.instrumentation.netty.common.HttpRequestAndChannel;
import java.util.List;
import javax.annotation.Nullable;

final class NettyHttpServerAttributesExtractor
    extends HttpServerAttributesExtractor<HttpRequestAndChannel, HttpResponse> {

  @Override
  protected String method(HttpRequestAndChannel requestAndChannel) {
    return requestAndChannel.request().getMethod().name();
  }

  @Override
  protected List<String> requestHeader(HttpRequestAndChannel requestAndChannel, String name) {
    return requestAndChannel.request().headers().getAll(name);
  }

  @Override
  @Nullable
  protected Long requestContentLength(
      HttpRequestAndChannel requestAndChannel, @Nullable HttpResponse response) {
    return null;
  }

  @Override
  @Nullable
  protected Long requestContentLengthUncompressed(
      HttpRequestAndChannel requestAndChannel, @Nullable HttpResponse response) {
    return null;
  }

  @Override
  protected Integer statusCode(HttpRequestAndChannel requestAndChannel, HttpResponse response) {
    return response.getStatus().code();
  }

  @Override
  @Nullable
  protected Long responseContentLength(
      HttpRequestAndChannel requestAndChannel, HttpResponse response) {
    return null;
  }

  @Override
  @Nullable
  protected Long responseContentLengthUncompressed(
      HttpRequestAndChannel requestAndChannel, HttpResponse response) {
    return null;
  }

  @Override
  protected List<String> responseHeader(
      HttpRequestAndChannel requestAndChannel, HttpResponse response, String name) {
    return response.headers().getAll(name);
  }

  @Override
  protected String flavor(HttpRequestAndChannel requestAndChannel) {
    String flavor = requestAndChannel.request().getProtocolVersion().toString();
    if (flavor.startsWith("HTTP/")) {
      flavor = flavor.substring("HTTP/".length());
    }
    return flavor;
  }

  @Override
  protected String target(HttpRequestAndChannel requestAndChannel) {
    return requestAndChannel.request().getUri();
  }

  @Override
  @Nullable
  protected String route(HttpRequestAndChannel requestAndChannel) {
    return null;
  }

  @Override
  protected String scheme(HttpRequestAndChannel requestAndChannel) {
    return getScheme(requestAndChannel);
  }

  @Override
  @Nullable
  protected String serverName(
      HttpRequestAndChannel requestAndChannel, @Nullable HttpResponse response) {
    return null;
  }
}
