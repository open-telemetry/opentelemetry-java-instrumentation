/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.apiclient;

import static io.opentelemetry.context.ContextKey.named;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import javax.annotation.Nullable;

public final class EndpointId {

  private static final ContextKey<String> KEY = named("elasticsearch-api-client-endpoint-id");

  public static Context storeInContext(Context context, String endpointId) {
    return context.with(KEY, endpointId);
  }

  @Nullable
  public static String get(Context context) {
    return context.get(KEY);
  }

  private EndpointId() {}
}
