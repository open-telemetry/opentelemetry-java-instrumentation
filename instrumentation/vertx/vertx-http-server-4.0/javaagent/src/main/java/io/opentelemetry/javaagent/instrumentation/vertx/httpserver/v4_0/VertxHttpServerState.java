/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.httpserver.v4_0;

import io.opentelemetry.context.Context;
import java.util.concurrent.atomic.AtomicBoolean;

public final class VertxHttpServerState {
  final Context context;
  final boolean started;
  final AtomicBoolean ended = new AtomicBoolean();

  VertxHttpServerState(Context context, boolean started) {
    this.context = context;
    this.started = started;
  }
}
