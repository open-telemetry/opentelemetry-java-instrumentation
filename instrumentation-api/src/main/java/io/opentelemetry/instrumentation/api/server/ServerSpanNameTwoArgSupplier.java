/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.server;

import io.opentelemetry.context.Context;
import javax.annotation.Nullable;

@FunctionalInterface
public interface ServerSpanNameTwoArgSupplier<T, U> {

  @Nullable
  String get(Context context, T arg1, U arg2);
}
