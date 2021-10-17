/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.twilio;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.Uninterruptibles;
import com.twilio.rest.api.v2010.account.Call;
import com.twilio.rest.api.v2010.account.Message;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class TwilioExperimentalAttributesExtractor implements AttributesExtractor<String, Object> {

  private static final Logger logger =
      LoggerFactory.getLogger(TwilioExperimentalAttributesExtractor.class);

  @Override
  public void onStart(AttributesBuilder attributes, String s) {}

  @Override
  public void onEnd(
      AttributesBuilder attributes, String s, @Nullable Object result, @Nullable Throwable error) {
    if (result == null) {
      return;
    }

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

    // Provide helpful metadata for some of the more common response types
    attributes.put("twilio.type", result.getClass().getCanonicalName());

    // Instrument the most popular resource types directly
    if (result instanceof Message) {
      Message message = (Message) result;
      attributes.put("twilio.account", message.getAccountSid());
      attributes.put("twilio.sid", message.getSid());
      Message.Status status = message.getStatus();
      if (status != null) {
        attributes.put("twilio.status", status.toString());
      }
    } else if (result instanceof Call) {
      Call call = (Call) result;
      attributes.put("twilio.account", call.getAccountSid());
      attributes.put("twilio.sid", call.getSid());
      attributes.put("twilio.parentSid", call.getParentCallSid());
      Call.Status status = call.getStatus();
      if (status != null) {
        attributes.put("twilio.status", status.toString());
      }
    } else {
      // Use reflection to gather insight from other types; note that Twilio requests take close to
      // 1 second, so the added hit from reflection here is relatively minimal in the grand scheme
      // of things
      setTagIfPresent(attributes, result, "twilio.sid", "getSid");
      setTagIfPresent(attributes, result, "twilio.account", "getAccountSid");
      setTagIfPresent(attributes, result, "twilio.status", "getStatus");
    }
  }

  /**
   * Helper method for calling a getter using reflection. This will be slow, so only use when
   * required.
   */
  private static void setTagIfPresent(
      AttributesBuilder attributes, Object result, String tag, String getter) {
    try {
      Method method = result.getClass().getMethod(getter);
      Object value = method.invoke(result);

      if (value != null) {
        attributes.put(tag, value.toString());
      }

    } catch (Exception e) {
      // Expected that this won't work for all result types
    }
  }
}
