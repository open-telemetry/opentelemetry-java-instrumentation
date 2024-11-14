/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack.v1_7.internal;

import ratpack.exec.ExecInitializer;
import ratpack.exec.Execution;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class OpenTelemetryExecInitializer implements ExecInitializer {
  public static final ExecInitializer INSTANCE = new OpenTelemetryExecInitializer();

  @Override
  public void init(Execution execution) {
    // Propagates ContextHolder to child execution because the response interceptor is triggered in
    // another execution segment
    execution
        .maybeParent()
        .flatMap(parent -> parent.maybeGet(ContextHolder.class))
        .ifPresent(execution::add);
  }
}
