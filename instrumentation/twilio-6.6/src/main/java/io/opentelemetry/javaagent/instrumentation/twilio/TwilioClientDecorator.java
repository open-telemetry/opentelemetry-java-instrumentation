/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.twilio;

import com.google.common.util.concurrent.ListenableFuture;
import com.twilio.rest.api.v2010.account.Call;
import com.twilio.rest.api.v2010.account.Message;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.instrumentation.api.decorator.ClientDecorator;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Decorate Twilio span's with relevant contextual information. */
public class TwilioClientDecorator extends ClientDecorator {

  private static final Logger log = LoggerFactory.getLogger(TwilioClientDecorator.class);

  public static final TwilioClientDecorator DECORATE = new TwilioClientDecorator();

  private static final Tracer TRACER =
      OpenTelemetry.getGlobalTracer("io.opentelemetry.auto.twilio");

  public static Tracer tracer() {
    return TRACER;
  }

  static final String COMPONENT_NAME = "twilio-sdk";

  /** Decorate trace based on service execution metadata. */
  public String spanNameOnServiceExecution(Object serviceExecutor, String methodName) {
    return spanNameForClass(serviceExecutor.getClass()) + "." + methodName;
  }

  /** Annotate the span with the results of the operation. */
  public Span onResult(Span span, Object result) {

    // Unwrap ListenableFuture (if present)
    if (result instanceof ListenableFuture) {
      try {
        result = ((ListenableFuture) result).get(0, TimeUnit.MICROSECONDS);
      } catch (Exception e) {
        log.debug("Error unwrapping result", e);
      }
    }

    // Nothing to do here, so return
    if (result == null) {
      return span;
    }

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
      // Use reflection to gather insight from other types; note that Twilio requests take close to
      // 1 second, so the added hit from reflection here is relatively minimal in the grand scheme
      // of things
      setTagIfPresent(span, result, "twilio.sid", "getSid");
      setTagIfPresent(span, result, "twilio.account", "getAccountSid");
      setTagIfPresent(span, result, "twilio.status", "getStatus");
    }

    return span;
  }

  /**
   * Helper method for calling a getter using reflection. This will be slow, so only use when
   * required.
   */
  private void setTagIfPresent(Span span, Object result, String tag, String getter) {
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
}
