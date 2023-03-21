/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.v4_1.internal.server;

import io.netty.handler.codec.http.HttpResponse;
import io.opentelemetry.context.Context;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public interface HttpServerResponseBeforeCommitHandler {
  void handle(Context context, HttpResponse response);

  /**
   * This class is internal and is hence not for public use. Its APIs are unstable and can change at
   * any time.
   */
  enum Noop implements HttpServerResponseBeforeCommitHandler {
    INSTANCE;

    @Override
    public void handle(Context context, HttpResponse response) {}
  }
}
