/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack;

import ratpack.error.ServerErrorHandler;
import ratpack.handling.Context;

final class OpenTelemetryServerErrorHandler implements ServerErrorHandler {

  static final ServerErrorHandler INSTANCE = new OpenTelemetryServerErrorHandler();

  private OpenTelemetryServerErrorHandler() {}

  @Override
  public void error(Context context, Throwable throwable) throws Exception {
    context
        .getExecution()
        .add(
            OpenTelemetryServerHandler.ErrorHolder.class,
            new OpenTelemetryServerHandler.ErrorHolder(throwable));

    ServerErrorHandler delegate = OpenTelemetryFallbackErrorHandler.INSTANCE;
    for (ServerErrorHandler errorHandler : context.getAll(ServerErrorHandler.class)) {
      if (errorHandler != INSTANCE) {
        delegate = errorHandler;
        break;
      }
    }

    delegate.error(context, throwable);
  }
}
