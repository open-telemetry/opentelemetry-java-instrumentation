/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import static java.lang.invoke.MethodType.methodType;

import com.amazonaws.AmazonWebServiceRequest;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;

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

  @Nullable private static final MethodHandle WITH_ATTRIBUTE_NAMES;
  @Nullable private static final MethodHandle GET_ATTRIBUTE_NAMES;

  static {
    Class<?> receiveMessageRequestClass = null;
    try {
      receiveMessageRequestClass =
          Class.forName("com.amazonaws.services.sqs.model.ReceiveMessageRequest");
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
                "withAttributeNames",
                methodType(receiveMessageRequestClass, String[].class));
      } catch (NoSuchMethodException | IllegalAccessException e) {
        // Ignore
      }
      WITH_ATTRIBUTE_NAMES = withAttributeNames;

      MethodHandle getAttributeNames = null;
      try {
        getAttributeNames =
            lookup.findVirtual(
                receiveMessageRequestClass, "getAttributeNames", methodType(List.class));
      } catch (NoSuchMethodException | IllegalAccessException e) {
        // Ignore
      }
      GET_ATTRIBUTE_NAMES = getAttributeNames;
    } else {
      WITH_ATTRIBUTE_NAMES = null;
      GET_ATTRIBUTE_NAMES = null;
    }
  }

  static boolean isInstance(AmazonWebServiceRequest request) {
    return request
        .getClass()
        .getName()
        .equals("com.amazonaws.services.sqs.model.ReceiveMessageRequest");
  }

  static void withAttributeNames(AmazonWebServiceRequest request, String name) {
    if (WITH_ATTRIBUTE_NAMES == null) {
      return;
    }
    try {
      WITH_ATTRIBUTE_NAMES.invoke(request, name);
    } catch (Throwable throwable) {
      // Ignore
    }
  }

  @SuppressWarnings("unchecked")
  static List<String> getAttributeNames(AmazonWebServiceRequest request) {
    if (GET_ATTRIBUTE_NAMES == null) {
      return Collections.emptyList();
    }
    try {
      return (List<String>) GET_ATTRIBUTE_NAMES.invoke(request);
    } catch (Throwable t) {
      return Collections.emptyList();
    }
  }

  private SqsReceiveMessageRequestAccess() {}
}
