/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4.common.client;

import io.opentelemetry.context.Context;
import javax.annotation.Nullable;

public interface NettySslInstrumenter {

  boolean shouldStart(Context parentContext, NettySslRequest request);

  Context start(Context parentContext, NettySslRequest request);

  void end(Context context, NettySslRequest request, @Nullable Throwable error);
}
