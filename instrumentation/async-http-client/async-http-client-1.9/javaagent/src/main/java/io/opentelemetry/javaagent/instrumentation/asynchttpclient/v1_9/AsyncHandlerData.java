/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asynchttpclient.v1_9;

import com.google.auto.value.AutoValue;
import com.ning.http.client.Request;
import io.opentelemetry.context.Context;

@AutoValue
public abstract class AsyncHandlerData {

  public static AsyncHandlerData create(Context parentContext, Context context, Request request) {
    return new AutoValue_AsyncHandlerData(parentContext, context, request);
  }

  public abstract Context getParentContext();

  public abstract Context getContext();

  public abstract Request getRequest();
}
