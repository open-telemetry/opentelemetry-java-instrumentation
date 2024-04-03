/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.semconv;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.semconv.incubating.EnduserIncubatingAttributes;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;

public class SemanticAttributes {

  private SemanticAttributes() {}

  @Deprecated
  public static final AttributeKey<String> MESSAGING_DESTINATION =
      AttributeKey.stringKey("messaging.destination");

  public static final class MessagingRocketmqMessageTypeValues {
    public static final String NORMAL =
        MessagingIncubatingAttributes.MessagingRocketmqMessageTypeValues.NORMAL;
    public static final String FIFO =
        MessagingIncubatingAttributes.MessagingRocketmqMessageTypeValues.FIFO;
    public static final String DELAY =
        MessagingIncubatingAttributes.MessagingRocketmqMessageTypeValues.DELAY;
    public static final String TRANSACTION =
        MessagingIncubatingAttributes.MessagingRocketmqMessageTypeValues.TRANSACTION;

    private MessagingRocketmqMessageTypeValues() {}
  }

  public static final String EXCEPTION_EVENT_NAME = "exception";

  public static final AttributeKey<String> FAAS_TRIGGER = ResourceAttributes.FAAS_TRIGGER;
  public static final AttributeKey<String> FAAS_INVOCATION_ID =
      ResourceAttributes.FAAS_INVOCATION_ID;

  public static final class FaasTriggerValues {
    private FaasTriggerValues() {}

    public static final String HTTP = ResourceAttributes.FaasTriggerValues.HTTP;
  }

  public static final AttributeKey<String> ENDUSER_ID = EnduserIncubatingAttributes.ENDUSER_ID;
  public static final AttributeKey<String> ENDUSER_ROLE = EnduserIncubatingAttributes.ENDUSER_ROLE;
  public static final AttributeKey<String> ENDUSER_SCOPE =
      EnduserIncubatingAttributes.ENDUSER_SCOPE;
}
