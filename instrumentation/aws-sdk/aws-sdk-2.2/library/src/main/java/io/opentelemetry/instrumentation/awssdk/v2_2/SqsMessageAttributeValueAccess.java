/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import static java.lang.invoke.MethodType.methodType;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import javax.annotation.Nullable;
import software.amazon.awssdk.core.SdkPojo;
import software.amazon.awssdk.utils.builder.SdkBuilder;

/**
 * Reflective access to aws-sdk-java-sqs class Message.
 *
 * <p>We currently don't have a good pattern of instrumenting a core library with various plugins
 * that need plugin-specific instrumentation - if we accessed the class directly, Muzzle would
 * prevent the entire instrumentation from loading when the plugin isn't available. We need to
 * carefully check this class has all reflection errors result in no-op, and in the future we will
 * hopefully come up with a better pattern.
 *
 * @see <a
 *     href="https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/services/sqs/model/MessageAttributeValue.html">SDK
 *     Javadoc</a>
 * @see <a
 *     href="https://github.com/aws/aws-sdk-java-v2/blob/2.2.0/services/sqs/src/main/resources/codegen-resources/service-2.json#L866-L896">Definition
 *     JSON</a>
 */
final class SqsMessageAttributeValueAccess {

  @Nullable private static final MethodHandle GET_STRING_VALUE;
  @Nullable private static final MethodHandle STRING_VALUE;
  @Nullable private static final MethodHandle DATA_TYPE;

  @Nullable private static final MethodHandle BUILDER;

  static {
    Class<?> messageAttributeValueClass = null;
    try {
      messageAttributeValueClass =
          Class.forName("software.amazon.awssdk.services.sqs.model.MessageAttributeValue");
    } catch (Throwable t) {
      // Ignore.
    }
    if (messageAttributeValueClass != null) {
      MethodHandles.Lookup lookup = MethodHandles.publicLookup();
      MethodHandle getStringValue = null;
      try {
        getStringValue =
            lookup.findVirtual(messageAttributeValueClass, "stringValue", methodType(String.class));
      } catch (NoSuchMethodException | IllegalAccessException e) {
        // Ignore
      }
      GET_STRING_VALUE = getStringValue;
    } else {
      GET_STRING_VALUE = null;
    }

    Class<?> builderClass = null;
    if (messageAttributeValueClass != null) {
      try {
        builderClass =
            Class.forName(
                "software.amazon.awssdk.services.sqs.model.MessageAttributeValue$Builder");
      } catch (Throwable t) {
        // Ignore.
      }
    }
    if (builderClass != null) {
      MethodHandles.Lookup lookup = MethodHandles.publicLookup();
      MethodHandle stringValue = null;
      try {
        stringValue =
            lookup.findVirtual(builderClass, "stringValue", methodType(builderClass, String.class));
      } catch (NoSuchMethodException | IllegalAccessException e) {
        // Ignore
      }
      STRING_VALUE = stringValue;

      MethodHandle dataType = null;
      try {
        dataType =
            lookup.findVirtual(builderClass, "dataType", methodType(builderClass, String.class));
      } catch (NoSuchMethodException | IllegalAccessException e) {
        // Ignore
      }
      DATA_TYPE = dataType;

      MethodHandle builder = null;
      try {
        builder =
            lookup.findStatic(messageAttributeValueClass, "builder", methodType(builderClass));
      } catch (NoSuchMethodException | IllegalAccessException e) {
        // Ignore
      }
      BUILDER = builder;
    } else {
      STRING_VALUE = null;
      DATA_TYPE = null;
      BUILDER = null;
    }
  }

  @SuppressWarnings({"rawtypes"})
  static String getStringValue(SdkPojo messageAttributeValue) {
    if (GET_STRING_VALUE == null) {
      return null;
    }
    try {
      return (String) GET_STRING_VALUE.invoke(messageAttributeValue);
    } catch (Throwable t) {
      return null;
    }
  }

  /**
   * Note that this does not set the (required) dataType automatically, see {@link
   * #dataType(SdkBuilder, String)} *
   */
  @SuppressWarnings({"rawtypes", "unchecked"})
  static SdkBuilder stringValue(SdkBuilder builder, String value) {
    if (STRING_VALUE == null) {
      return null;
    }
    try {
      return (SdkBuilder) STRING_VALUE.invoke(builder, value);
    } catch (Throwable t) {
      return null;
    }
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  static SdkBuilder dataType(SdkBuilder builder, String dataType) {
    if (DATA_TYPE == null) {
      return null;
    }
    try {
      return (SdkBuilder) DATA_TYPE.invoke(builder, dataType);
    } catch (Throwable t) {
      return null;
    }
  }

  private SqsMessageAttributeValueAccess() {}

  @SuppressWarnings({"rawtypes"})
  public static SdkBuilder builder() {
    if (BUILDER == null) {
      return null;
    }

    try {
      return (SdkBuilder) BUILDER.invoke();
    } catch (Throwable e) {
      return null;
    }
  }
}
