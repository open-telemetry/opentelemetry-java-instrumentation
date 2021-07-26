/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.common.client;

import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;

public abstract class AbstractNettyRequestWrapper {

  protected final HttpRequest request;

  protected AbstractNettyRequestWrapper(HttpRequest request) {
    this.request = request;
  }

  public final HttpRequest request() {
    return request;
  }

  public final HttpHeaders headers() {
    return request.headers();
  }

  public abstract boolean isHttps();

  public abstract String getHostHeader();

  public abstract HttpVersion protocolVersion();

  public abstract String uri();

  public abstract HttpMethod method();
}
