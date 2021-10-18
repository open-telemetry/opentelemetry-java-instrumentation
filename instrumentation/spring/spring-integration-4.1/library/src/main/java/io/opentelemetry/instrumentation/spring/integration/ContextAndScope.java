/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.integration;

import com.google.auto.value.AutoValue;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import javax.annotation.Nullable;

@AutoValue
abstract class ContextAndScope {

  @Nullable
  abstract Context getContext();

  abstract Scope getScope();

  void close() {
    getScope().close();
  }

  static ContextAndScope create(Context context, Scope scope) {
    return new AutoValue_ContextAndScope(context, scope);
  }
}
