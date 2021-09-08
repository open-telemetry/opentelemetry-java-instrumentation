/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.kafka;

import com.google.auto.value.AutoValue;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.apache.kafka.clients.consumer.ConsumerRecords;

@AutoValue
public abstract class State<K, V> {

  public static <K, V> State<K, V> create(
      ConsumerRecords<K, V> request, Context context, Scope scope) {
    return new AutoValue_State<>(request, context, scope);
  }

  public abstract ConsumerRecords<K, V> request();

  public abstract Context context();

  public abstract Scope scope();

  State() {}
}
