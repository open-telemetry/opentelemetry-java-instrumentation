/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.kafka.v2_7;

import com.google.auto.value.AutoValue;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

@AutoValue
abstract class State<REQUEST> {

  static <REQUEST> State<REQUEST> create(REQUEST request, Context context, Scope scope) {
    return new AutoValue_State<>(request, context, scope);
  }

  abstract REQUEST request();

  abstract Context context();

  abstract Scope scope();

  State() {}
}
