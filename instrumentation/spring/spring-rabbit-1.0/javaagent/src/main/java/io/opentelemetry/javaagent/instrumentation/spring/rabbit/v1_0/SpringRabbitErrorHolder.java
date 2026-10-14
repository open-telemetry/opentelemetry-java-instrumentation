/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.rabbit.v1_0;

import static io.opentelemetry.context.ContextKey.named;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.ImplicitContextKeyed;
import javax.annotation.Nullable;

public class SpringRabbitErrorHolder implements ImplicitContextKeyed {

  private static final ContextKey<SpringRabbitErrorHolder> KEY =
      named("opentelemetry-spring-rabbit-error");

  @Nullable private Throwable error;

  private SpringRabbitErrorHolder() {}

  public static Context init(Context context) {
    return context.with(new SpringRabbitErrorHolder());
  }

  public static void set(Context context, Throwable error) {
    SpringRabbitErrorHolder holder = context.get(KEY);
    if (holder != null) {
      holder.error = error;
    }
  }

  @Nullable
  public static Throwable getOrDefault(Context context, @Nullable Throwable error) {
    SpringRabbitErrorHolder holder = context.get(KEY);
    return holder == null || holder.error == null ? error : holder.error;
  }

  @Override
  public Context storeInContext(Context context) {
    return context.with(KEY, this);
  }
}
