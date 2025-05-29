/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.v4_1.internal;

import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.opentelemetry.context.Context;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public interface ProtocolEventHandler {
  void handle(
      ProtocolSpecificEvent event, Context context, HttpRequest request, HttpResponse response);

  /**
   * This class is internal and is hence not for public use. Its APIs are unstable and can change at
   * any time.
   */
  enum Noop implements ProtocolEventHandler {
    INSTANCE;

    @Override
    public void handle(
        ProtocolSpecificEvent event, Context context, HttpRequest request, HttpResponse response) {}
  }

  /**
   * This class is internal and is hence not for public use. Its APIs are unstable and can change at
   * any time.
   */
  enum Enabled implements ProtocolEventHandler {
    INSTANCE;

    @Override
    public void handle(
        ProtocolSpecificEvent event, Context context, HttpRequest request, HttpResponse response) {
      event.addEvent(context, request, response);
    }
  }
}
