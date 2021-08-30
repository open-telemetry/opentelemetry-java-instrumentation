/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.servlet;

import io.opentelemetry.context.Context;
import org.checkerframework.checker.nullness.qual.Nullable;

public interface ServerSpanNameSupplier<T> {

  @Nullable
  String get(Context context, T arg);
}
