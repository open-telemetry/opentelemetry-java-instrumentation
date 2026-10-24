/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.httpserver.v4_0;

import io.vertx.core.http.HttpServerRequest;
import java.lang.ref.WeakReference;

public final class VertxHttpServerResponseState {
  final WeakReference<HttpServerRequest> request;
  final VertxHttpServerState state;

  VertxHttpServerResponseState(HttpServerRequest request, VertxHttpServerState state) {
    // The request owns its response, so a strong back-reference would leak fallback virtual fields.
    this.request = new WeakReference<>(request);
    this.state = state;
  }
}
