/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.finatra;

import static io.opentelemetry.javaagent.instrumentation.finatra.FinatraSingletons.THROWABLE;
import static io.opentelemetry.javaagent.instrumentation.finatra.FinatraSingletons.instrumenter;

import com.twitter.finagle.http.Response;
import com.twitter.util.FutureEventListener;
import io.opentelemetry.context.Context;

public final class FinatraResponseListener implements FutureEventListener<Response> {

  private final Context context;
  private final FinatraRequest request;

  public FinatraResponseListener(Context context, FinatraRequest request) {
    this.context = context;
    this.request = request;
  }

  @Override
  public void onSuccess(Response response) {
    Throwable throwable = THROWABLE.get(response);
    instrumenter().end(context, request, null, throwable);
  }

  @Override
  public void onFailure(Throwable cause) {
    instrumenter().end(context, request, null, cause);
  }
}
