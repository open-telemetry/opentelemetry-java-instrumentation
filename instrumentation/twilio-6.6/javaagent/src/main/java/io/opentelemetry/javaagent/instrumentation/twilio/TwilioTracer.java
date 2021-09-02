/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.twilio;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.Uninterruptibles;
import com.twilio.rest.api.v2010.account.Call;
import com.twilio.rest.api.v2010.account.Message;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import io.opentelemetry.instrumentation.api.tracer.SpanNames;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TwilioTracer extends BaseTracer {

  private static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
      Config.get().getBoolean("otel.instrumentation.twilio.experimental-span-attributes", false);

  private static final Logger logger = LoggerFactory.getLogger(TwilioTracer.class);

  public static final TwilioTracer TRACER = new TwilioTracer();

  public static TwilioTracer tracer() {
    return TRACER;
  }

  public boolean shouldStartSpan(Context parentContext) {
    return shouldStartSpan(parentContext, CLIENT);
  }

  public Context startSpan(Context parentContext, Object serviceExecutor, String methodName) {
    String spanName = spanNameOnServiceExecution(serviceExecutor, methodName);
    Span span = spanBuilder(parentContext, spanName, CLIENT).startSpan();
    return withClientSpan(parentContext, span);
  }

  /** Decorate trace based on service execution metadata. */
  private static String spanNameOnServiceExecution(Object serviceExecutor, String methodName) {
    return SpanNames.fromMethod(serviceExecutor.getClass(), methodName);
  }

  /** Annotate the span with the results of the operation. */
  public void end(Context context, Object result) {

    // Unwrap ListenableFuture (if present)
    if (result instanceof ListenableFuture) {
      try {
        result =
            Uninterruptibles.getUninterruptibly(
                (ListenableFuture<?>) result, 0, TimeUnit.MICROSECONDS);
      } catch (Exception e) {
        logger.debug("Error unwrapping result", e);
      }
    }

    Span span = Span.fromContext(context);

    // Nothing to do here, so return
    if (result == null) {
      span.end();
      return;
    }

    if (CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
      // Provide helpful metadata for some of the more common response types
      span.setAttribute("twilio.type", result.getClass().getCanonicalName());

      // Instrument the most popular resource types directly
      if (result instanceof Message) {
        Message message = (Message) result;
        span.setAttribute("twilio.account", message.getAccountSid());
        span.setAttribute("twilio.sid", message.getSid());
        Message.Status status = message.getStatus();
        if (status != null) {
          span.setAttribute("twilio.status", status.toString());
        }
      } else if (result instanceof Call) {
        Call call = (Call) result;
        span.setAttribute("twilio.account", call.getAccountSid());
        span.setAttribute("twilio.sid", call.getSid());
        span.setAttribute("twilio.parentSid", call.getParentCallSid());
        Call.Status status = call.getStatus();
        if (status != null) {
          span.setAttribute("twilio.status", status.toString());
        }
      } else {
        // Use reflection to gather insight from other types; note that Twilio requests take close
        // to
        // 1 second, so the added hit from reflection here is relatively minimal in the grand scheme
        // of things
        setTagIfPresent(span, result, "twilio.sid", "getSid");
        setTagIfPresent(span, result, "twilio.account", "getAccountSid");
        setTagIfPresent(span, result, "twilio.status", "getStatus");
      }
    }

    super.end(context);
  }

  /**
   * Helper method for calling a getter using reflection. This will be slow, so only use when
   * required.
   */
  private static void setTagIfPresent(Span span, Object result, String tag, String getter) {
    try {
      Method method = result.getClass().getMethod(getter);
      Object value = method.invoke(result);

      if (value != null) {
        span.setAttribute(tag, value.toString());
      }

    } catch (Exception e) {
      // Expected that this won't work for all result types
    }
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.twilio-6.6";
  }
}
