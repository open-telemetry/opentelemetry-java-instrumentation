/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.finatra;

import static io.opentelemetry.javaagent.instrumentation.finatra.FinatraSingletons.instrumenter;

import com.twitter.finagle.http.Response;
import com.twitter.util.FutureEventListener;
import io.opentelemetry.context.Context;

public final class FinatraResponseListener implements FutureEventListener<Response> {

  private final Context context;
  private final Class<?> request;

  public FinatraResponseListener(Context context, Class<?> request) {
    this.context = context;
    this.request = request;
  }

  @Override
  public void onSuccess(Response response) {
    instrumenter().end(context, request, null, null);
  }

  @Override
  public void onFailure(Throwable cause) {
    instrumenter().end(context, request, null, cause);
  }
}
