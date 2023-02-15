/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.kafka.v2_7;

import com.google.auto.value.AutoValue;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

@AutoValue
abstract class State {

  static State create(Context context, Scope scope) {
    return new AutoValue_State(context, scope);
  }

  abstract Context context();

  abstract Scope scope();

  State() {}
}
