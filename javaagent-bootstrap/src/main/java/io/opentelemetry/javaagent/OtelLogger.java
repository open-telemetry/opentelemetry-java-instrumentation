/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.Value;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.context.Context;
import javax.annotation.Nullable;

@FunctionalInterface
public interface OtelLogger {

  void record(
      Context context,
      String scopeName,
      @Nullable String eventName,
      @Nullable Value<?> body,
      Attributes attributes,
      Severity severity);
}
