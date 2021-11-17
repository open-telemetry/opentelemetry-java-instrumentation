/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.common.client;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import javax.annotation.Nullable;

final class NettySslErrorOnlyInstrumenter implements NettySslInstrumenter {

  private final Instrumenter<NettySslRequest, Void> instrumenter;

  NettySslErrorOnlyInstrumenter(Instrumenter<NettySslRequest, Void> instrumenter) {
    this.instrumenter = instrumenter;
  }

  @Override
  public boolean shouldStart(Context parentContext, NettySslRequest request) {
    // the "real" check is done on end() anyway
    return true;
  }

  @Override
  public Context start(Context parentContext, NettySslRequest request) {
    return parentContext;
  }

  @Override
  public void end(Context context, NettySslRequest request, @Nullable Throwable error) {
    if (error != null && instrumenter.shouldStart(context, request)) {
      Context connectContext = instrumenter.start(context, request);
      instrumenter.end(connectContext, request, null, error);
    }
  }
}
