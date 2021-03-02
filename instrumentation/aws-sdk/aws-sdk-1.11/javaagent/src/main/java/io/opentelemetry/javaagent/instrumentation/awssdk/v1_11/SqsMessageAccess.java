/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v1_11;

import static java.lang.invoke.MethodType.methodType;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Collections;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Reflective access to aws-sdk-java-sqs class Message.
 *
 * <p>We currently don't have a good pattern of instrumenting a core library with various plugins
 * that need plugin-specific instrumentation - if we accessed the class directly, Muzzle would
 * prevent the entire instrumentation from loading when the plugin isn't available. We need to
 * carefully check this class has all reflection errors result in no-op, and in the future we will
 * hopefully come up with a better pattern.
 */
final class SqsMessageAccess {

  @Nullable private static final MethodHandle GET_ATTRIBUTES;

  static {
    Class<?> messageClass = null;
    try {
      messageClass = Class.forName("com.amazonaws.services.sqs.model.Message");
    } catch (Throwable t) {
      // Ignore.
    }
    if (messageClass != null) {
      MethodHandles.Lookup lookup = MethodHandles.publicLookup();
      MethodHandle getAttributes = null;
      try {
        getAttributes = lookup.findVirtual(messageClass, "getAttributes", methodType(Map.class));
      } catch (NoSuchMethodException | IllegalAccessException e) {
        // Ignore
      }
      GET_ATTRIBUTES = getAttributes;
    } else {
      GET_ATTRIBUTES = null;
    }
  }

  @SuppressWarnings("unchecked")
  static Map<String, String> getAttributes(Object message) {
    if (GET_ATTRIBUTES == null) {
      return Collections.emptyMap();
    }
    try {
      return (Map<String, String>) GET_ATTRIBUTES.invoke(message);
    } catch (Throwable t) {
      return Collections.emptyMap();
    }
  }

  private SqsMessageAccess() {}
}
