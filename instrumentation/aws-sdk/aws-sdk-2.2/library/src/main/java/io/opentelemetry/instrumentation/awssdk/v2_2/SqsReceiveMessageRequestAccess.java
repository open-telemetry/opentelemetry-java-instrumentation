/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import static java.lang.invoke.MethodType.methodType;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;
import software.amazon.awssdk.core.SdkRequest;

/**
 * Reflective access to aws-sdk-java-sqs class ReceiveMessageRequest.
 *
 * <p>We currently don't have a good pattern of instrumenting a core library with various plugins
 * that need plugin-specific instrumentation - if we accessed the class directly, Muzzle would
 * prevent the entire instrumentation from loading when the plugin isn't available. We need to
 * carefully check this class has all reflection errors result in no-op, and in the future we will
 * hopefully come up with a better pattern.
 */
final class SqsReceiveMessageRequestAccess {

  @Nullable private static final MethodHandle ATTRIBUTE_NAMES_WITH_STRINGS;

  static {
    Class<?> receiveMessageRequestClass = null;
    try {
      receiveMessageRequestClass =
          Class.forName("software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest$Builder");
    } catch (Throwable t) {
      // Ignore.
    }
    if (receiveMessageRequestClass != null) {
      MethodHandles.Lookup lookup = MethodHandles.publicLookup();
      MethodHandle withAttributeNames = null;
      try {
        withAttributeNames =
            lookup.findVirtual(
                receiveMessageRequestClass,
                "attributeNamesWithStrings",
                methodType(receiveMessageRequestClass, Collection.class));
      } catch (NoSuchMethodException | IllegalAccessException e) {
        // Ignore
      }
      ATTRIBUTE_NAMES_WITH_STRINGS = withAttributeNames;
    } else {
      ATTRIBUTE_NAMES_WITH_STRINGS = null;
    }
  }

  static boolean isInstance(SdkRequest request) {
    return request
        .getClass()
        .getName()
        .equals("software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest");
  }

  static void attributeNamesWithStrings(SdkRequest.Builder builder, List<String> attributeNames) {
    if (ATTRIBUTE_NAMES_WITH_STRINGS == null) {
      return;
    }
    try {
      ATTRIBUTE_NAMES_WITH_STRINGS.invoke(builder, attributeNames);
    } catch (Throwable throwable) {
      // Ignore
    }
  }

  private SqsReceiveMessageRequestAccess() {}
}
