/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkastreams;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.apache.kafka.clients.consumer.ConsumerRecord;

public final class StateHolder {
  public static final ThreadLocal<StateHolder> HOLDER = new ThreadLocal<>();

  private ConsumerRecord<?, ?> record;
  private Context context;
  private Scope scope;

  public void closeScope() {
    scope.close();
  }

  public ConsumerRecord<?, ?> getRecord() {
    return record;
  }

  public Context getContext() {
    return context;
  }

  public void set(ConsumerRecord<?, ?> record, Context context, Scope scope) {
    this.record = record;
    this.context = context;
    this.scope = scope;
  }
}
