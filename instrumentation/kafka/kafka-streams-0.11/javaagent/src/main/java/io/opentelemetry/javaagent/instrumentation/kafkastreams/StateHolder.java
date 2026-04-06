/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkastreams;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaProcessRequest;
import javax.annotation.Nullable;

public class StateHolder {
  public static final ThreadLocal<StateHolder> HOLDER = new ThreadLocal<>();

  @Nullable private KafkaProcessRequest request;
  @Nullable private Context context;
  @Nullable private Scope scope;

  public void closeScope() {
    scope.close();
  }

  @Nullable
  public KafkaProcessRequest getRequest() {
    return request;
  }

  @Nullable
  public Context getContext() {
    return context;
  }

  public void set(KafkaProcessRequest request, Context context, Scope scope) {
    this.request = request;
    this.context = context;
    this.scope = scope;
  }
}
