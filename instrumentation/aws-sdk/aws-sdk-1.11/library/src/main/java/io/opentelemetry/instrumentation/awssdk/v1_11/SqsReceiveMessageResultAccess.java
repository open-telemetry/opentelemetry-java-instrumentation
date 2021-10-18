/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import static java.lang.invoke.MethodType.methodType;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Reflective access to aws-sdk-java-sqs class ReceiveMessageResult.
 *
 * <p>We currently don't have a good pattern of instrumenting a core library with various plugins
 * that need plugin-specific instrumentation - if we accessed the class directly, Muzzle would
 * prevent the entire instrumentation from loading when the plugin isn't available. We need to
 * carefully check this class has all reflection errors result in no-op, and in the future we will
 * hopefully come up with a better pattern.
 */
final class SqsReceiveMessageResultAccess {

  @Nullable private static final MethodHandle GET_MESSAGES;

  static {
    Class<?> receiveMessageResultClass = null;
    try {
      receiveMessageResultClass =
          Class.forName("com.amazonaws.services.sqs.model.ReceiveMessageResult");
    } catch (Throwable t) {
      // Ignore.
    }
    if (receiveMessageResultClass != null) {
      MethodHandles.Lookup lookup = MethodHandles.publicLookup();
      MethodHandle getMessages = null;
      try {
        getMessages =
            lookup.findVirtual(receiveMessageResultClass, "getMessages", methodType(List.class));
      } catch (NoSuchMethodException | IllegalAccessException e) {
        // Ignore
      }
      GET_MESSAGES = getMessages;
    } else {
      GET_MESSAGES = null;
    }
  }

  static List<?> getMessages(Object result) {
    if (GET_MESSAGES == null) {
      return Collections.emptyList();
    }
    try {
      return (List<?>) GET_MESSAGES.invoke(result);
    } catch (Throwable t) {
      return Collections.emptyList();
    }
  }

  private SqsReceiveMessageResultAccess() {}
}
