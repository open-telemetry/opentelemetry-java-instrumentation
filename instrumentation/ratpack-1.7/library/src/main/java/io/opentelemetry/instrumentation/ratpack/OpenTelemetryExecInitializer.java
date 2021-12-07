/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack;

import io.opentelemetry.context.Context;
import ratpack.exec.ExecInitializer;
import ratpack.exec.Execution;
import ratpack.http.client.RequestSpec;

public final class OpenTelemetryExecInitializer implements ExecInitializer {

  @Override
  public void init(Execution execution) {
    execution
        .maybeParent()
        .flatMap(parent -> parent.maybeGet(ContextHolder.class))
        .ifPresent(execution::add);
  }

  public static final class ContextHolder {
    private final Context context;
    private final RequestSpec requestSpec;

    public ContextHolder(Context context, RequestSpec requestSpec) {
      this.context = context;
      this.requestSpec = requestSpec;
    }

    public Context context() {
      return context;
    }

    public RequestSpec requestSpec() {
      return requestSpec;
    }
  }
}
