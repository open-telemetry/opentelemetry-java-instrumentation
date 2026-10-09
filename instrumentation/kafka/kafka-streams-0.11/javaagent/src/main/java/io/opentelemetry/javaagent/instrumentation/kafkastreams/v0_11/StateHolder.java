/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkastreams.v0_11;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.internal.ScopedThreadValue;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaProcessRequest;
import javax.annotation.Nullable;

public class StateHolder {
  private static final ScopedThreadValue<StateHolder> holder = new ScopedThreadValue<>();

  @Nullable private StateHolder previous;
  @Nullable private KafkaProcessRequest request;
  @Nullable private Context context;
  @Nullable private Scope scope;

  public static ScopedThreadValue<StateHolder> holder() {
    return holder;
  }

  @Nullable
  public StateHolder getPrevious() {
    return previous;
  }

  public void setPrevious(@Nullable StateHolder previous) {
    this.previous = previous;
  }

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
