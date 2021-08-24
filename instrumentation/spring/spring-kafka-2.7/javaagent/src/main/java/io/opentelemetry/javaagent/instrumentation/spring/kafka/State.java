/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.kafka;

import com.google.auto.value.AutoValue;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

@AutoValue
public abstract class State<K, V> {

  public static <K, V> State<K, V> create(
      BatchRecords<K, V> request, Context context, Scope scope) {
    return new AutoValue_State<>(request, context, scope);
  }

  public abstract BatchRecords<K, V> request();

  public abstract Context context();

  public abstract Scope scope();

  State() {}
}
