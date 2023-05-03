/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import static java.lang.invoke.MethodType.methodType;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
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
 *
 * @see <a
 *     href="https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/services/sqs/model/ReceiveMessageRequest.html">SDK
 *     Javadoc</a>
 * @see <a
 *     href="https://github.com/aws/aws-sdk-java-v2/blob/2.2.0/services/sqs/src/main/resources/codegen-resources/service-2.json#L1076-L1110">Definition
 *     JSON</a>
 */
final class SqsReceiveMessageRequestAccess {

  @Nullable private static final MethodHandle ATTRIBUTE_NAMES_WITH_STRINGS;
  @Nullable private static final MethodHandle MESSAGE_ATTRIBUTE_NAMES;

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

      MethodHandle messageAttributeNames = null;
      try {
        messageAttributeNames =
            lookup.findVirtual(
                receiveMessageRequestClass,
                "messageAttributeNames",
                methodType(receiveMessageRequestClass, Collection.class));
      } catch (NoSuchMethodException | IllegalAccessException e) {
        // Ignore
      }
      MESSAGE_ATTRIBUTE_NAMES = messageAttributeNames;
    } else {
      ATTRIBUTE_NAMES_WITH_STRINGS = null;
      MESSAGE_ATTRIBUTE_NAMES = null;
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

  static void messageAttributeNames(
      SdkRequest.Builder builder, List<String> messageAttributeNames) {
    if (MESSAGE_ATTRIBUTE_NAMES == null) {
      return;
    }
    try {
      MESSAGE_ATTRIBUTE_NAMES.invoke(builder, messageAttributeNames);
    } catch (Throwable throwable) {
      // Ignore
    }
  }

  private SqsReceiveMessageRequestAccess() {}

  @SuppressWarnings({"rawtypes", "unchecked"})
  static List<String> getAttributeNames(SdkRequest request) {
    Optional<List> optional = request.getValueForField("AttributeNames", List.class);
    return optional.isPresent() ? (List<String>) optional.get() : Collections.emptyList();
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  static List<String> getMessageAttributeNames(SdkRequest request) {
    Optional<List> optional = request.getValueForField("MessageAttributeNames", List.class);
    return optional.isPresent() ? (List<String>) optional.get() : Collections.emptyList();
  }
}
