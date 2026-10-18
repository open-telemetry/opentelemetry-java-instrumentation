/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.wicket.v8_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.LocalRootSpan;
import io.opentelemetry.instrumentation.api.internal.ExceptionRecorder;
import io.opentelemetry.instrumentation.api.internal.RecordedExceptions;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.semconv.SchemaUrls;
import org.apache.wicket.request.cycle.IRequestCycleListener;
import org.apache.wicket.request.cycle.RequestCycle;

public final class WicketExceptionRecorder {
  private static final Logger logger =
      ExceptionRecorder.createLogger(
          GlobalOpenTelemetry.get(), "io.opentelemetry.wicket-8.0", SchemaUrls.V1_41_0);

  private static final VirtualField<RequestCycle, RecordedExceptions> RECORDED_EXCEPTIONS =
      VirtualField.find(RequestCycle.class, RecordedExceptions.class);

  public static void record(Context context, Exception exception) {
    Throwable error = WicketErrorUnwrapper.unwrap(exception);
    RequestCycle cycle = RequestCycle.get();
    if (cycle != null) {
      RecordedExceptions recorded = RECORDED_EXCEPTIONS.get(cycle);
      if (recorded == null) {
        recorded = new RecordedExceptions();
        RECORDED_EXCEPTIONS.set(cycle, recorded);
        RecordedExceptions toClear = recorded;
        cycle
            .getListeners()
            .add(
                new IRequestCycleListener() {
                  @Override
                  public void onDetach(RequestCycle requestCycle) {
                    toClear.close();
                    RECORDED_EXCEPTIONS.set(requestCycle, null);
                  }
                });
      }
      if (!recorded.add(error)) {
        return;
      }
    }
    ExceptionRecorder.recordServerException(
        context, LocalRootSpan.fromContextOrNull(context), error, logger);
  }

  private WicketExceptionRecorder() {}
}
