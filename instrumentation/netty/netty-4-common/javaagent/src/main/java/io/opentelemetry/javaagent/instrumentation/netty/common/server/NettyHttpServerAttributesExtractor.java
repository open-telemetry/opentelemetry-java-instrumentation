/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.common.server;

import io.netty.handler.codec.http.HttpResponse;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesExtractor;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

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
  protected @Nullable Long requestContentLength(
      HttpRequestAndChannel requestAndChannel, @Nullable HttpResponse response) {
    return null;
  }

  @Override
  protected @Nullable Long requestContentLengthUncompressed(
      HttpRequestAndChannel requestAndChannel, @Nullable HttpResponse response) {
    return null;
  }

  @Override
  protected Integer statusCode(HttpRequestAndChannel requestAndChannel, HttpResponse response) {
    return response.getStatus().code();
  }

  @Override
  protected @Nullable Long responseContentLength(
      HttpRequestAndChannel requestAndChannel, HttpResponse response) {
    return null;
  }

  @Override
  protected @Nullable Long responseContentLengthUncompressed(
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
  protected @Nullable String route(HttpRequestAndChannel requestAndChannel) {
    return null;
  }

  @Override
  protected @Nullable String scheme(HttpRequestAndChannel requestAndChannel) {
    return null;
  }

  @Override
  protected @Nullable String serverName(
      HttpRequestAndChannel requestAndChannel, @Nullable HttpResponse response) {
    return null;
  }
}
