/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asynchttpclient.common;

import com.google.auto.value.AutoValue;
import com.ning.http.client.Request;
import com.ning.http.client.Response;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;

@AutoValue
public abstract class AsyncHandlerData {

  public static AsyncHandlerData create(
      Context parentContext, 
      Context context, 
      Request request, 
      Instrumenter<Request, Response> instrumenter) {
    return new AutoValue_AsyncHandlerData(parentContext, context, request, instrumenter);
  }

  public abstract Context getParentContext();

  public abstract Context getContext();

  public abstract Request getRequest();

  public abstract Instrumenter<Request, Response> getInstrumenter();
}
