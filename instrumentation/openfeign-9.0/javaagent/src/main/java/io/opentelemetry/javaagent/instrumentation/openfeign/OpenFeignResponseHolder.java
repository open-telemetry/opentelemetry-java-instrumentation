/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.openfeign;

import static io.opentelemetry.context.ContextKey.named;

import feign.Response;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.ImplicitContextKeyed;
import javax.annotation.Nullable;

public class OpenFeignResponseHolder implements ImplicitContextKeyed {

  private static final ContextKey<OpenFeignResponseHolder> KEY =
      named("opentelemetry-openfeign-response");

  private Response response;

  public static Context init(Context context) {
    if (context.get(KEY) != null) {
      return context;
    }
    return context.with(new OpenFeignResponseHolder());
  }

  public static void set(Context context, Response response) {
    OpenFeignResponseHolder holder = context.get(KEY);
    if (holder != null) {
      holder.response = response;
    }
  }

  @Nullable
  public static Response get(Context context) {
    OpenFeignResponseHolder holder = context.get(KEY);
    if (holder != null) {
      return holder.response;
    }
    return null;
  }

  @Override
  public Context storeInContext(Context context) {
    return context.with(KEY, this);
  }
}
