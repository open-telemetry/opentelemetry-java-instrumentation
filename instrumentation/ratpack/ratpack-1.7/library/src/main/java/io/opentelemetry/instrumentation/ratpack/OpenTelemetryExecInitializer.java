/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack;

import io.opentelemetry.instrumentation.ratpack.internal.ContextHolder;
import ratpack.exec.ExecInitializer;
import ratpack.exec.Execution;

final class OpenTelemetryExecInitializer implements ExecInitializer {
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
