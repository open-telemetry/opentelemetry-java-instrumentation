/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import static java.lang.invoke.MethodType.methodType;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import software.amazon.awssdk.core.SdkPojo;
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
 *     href="https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/services/sqs/model/SendMessageRequest.html">SDK
 *     Javadoc</a>
 * @see <a
 *     href="https://github.com/aws/aws-sdk-java-v2/blob/2.2.0/services/sqs/src/main/resources/codegen-resources/service-2.json#L1257-L1291">Definition
 *     JSON</a>
 */
final class SqsSendMessageRequestAccess {

  @Nullable private static final MethodHandle MESSAGE_ATTRIBUTES;

  static {
    Class<?> sendMessageRequestClass = null;
    try {
      sendMessageRequestClass =
          Class.forName("software.amazon.awssdk.services.sqs.model.SendMessageRequest$Builder");
    } catch (Throwable t) {
      // Ignore.
    }
    if (sendMessageRequestClass != null) {
      MethodHandles.Lookup lookup = MethodHandles.publicLookup();
      MethodHandle messageAttributes = null;
      try {
        messageAttributes =
            lookup.findVirtual(
                sendMessageRequestClass,
                "messageAttributes",
                methodType(sendMessageRequestClass, Map.class));
      } catch (NoSuchMethodException | IllegalAccessException e) {
        // Ignore
      }
      MESSAGE_ATTRIBUTES = messageAttributes;
    } else {
      MESSAGE_ATTRIBUTES = null;
    }
  }

  static boolean isInstance(SdkRequest request) {
    return request
        .getClass()
        .getName()
        .equals("software.amazon.awssdk.services.sqs.model.SendMessageRequest");
  }

  static void messageAttributes(
      SdkRequest.Builder builder, Map<String, SdkPojo> messageAttributes) {
    if (MESSAGE_ATTRIBUTES == null) {
      return;
    }
    try {
      MESSAGE_ATTRIBUTES.invoke(builder, messageAttributes);
    } catch (Throwable throwable) {
      // Ignore
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  static Map<String, SdkPojo> messageAttributes(SdkRequest request) {
    Optional<Map> optional = request.getValueForField("AttributeNames", Map.class);
    return optional.isPresent() ? (Map<String, SdkPojo>) optional.get() : Collections.emptyMap();
  }

  private SqsSendMessageRequestAccess() {}
}
