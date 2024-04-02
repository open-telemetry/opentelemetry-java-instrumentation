/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.semconv;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.semconv.incubating.CodeIncubatingAttributes;
import io.opentelemetry.semconv.incubating.EnduserIncubatingAttributes;
import io.opentelemetry.semconv.incubating.ExceptionIncubatingAttributes;
import io.opentelemetry.semconv.incubating.MessageIncubatingAttributes;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import io.opentelemetry.semconv.incubating.PeerIncubatingAttributes;
import io.opentelemetry.semconv.incubating.ThreadIncubatingAttributes;

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

  public static final AttributeKey<String> CODE_FILEPATH = CodeIncubatingAttributes.CODE_FILEPATH;
  public static final AttributeKey<String> CODE_FUNCTION = CodeIncubatingAttributes.CODE_FUNCTION;
  public static final AttributeKey<Long> CODE_LINENO = CodeIncubatingAttributes.CODE_LINENO;
  public static final AttributeKey<String> CODE_NAMESPACE = CodeIncubatingAttributes.CODE_NAMESPACE;

  public static final AttributeKey<String> PEER_SERVICE = PeerIncubatingAttributes.PEER_SERVICE;

  public static final AttributeKey<Long> MESSAGE_ID = MessageIncubatingAttributes.MESSAGE_ID;
  public static final AttributeKey<String> MESSAGE_TYPE = MessageIncubatingAttributes.MESSAGE_TYPE;

  public static final class MessageTypeValues {
    public static final String SENT = MessageIncubatingAttributes.MessageTypeValues.SENT;
    public static final String RECEIVED = MessageIncubatingAttributes.MessageTypeValues.RECEIVED;

    private MessageTypeValues() {}
  }

  public static final AttributeKey<Long> THREAD_ID = ThreadIncubatingAttributes.THREAD_ID;
  public static final AttributeKey<String> THREAD_NAME = ThreadIncubatingAttributes.THREAD_NAME;

  public static final AttributeKey<String> EXCEPTION_MESSAGE =
      ExceptionIncubatingAttributes.EXCEPTION_MESSAGE;
  public static final AttributeKey<String> EXCEPTION_STACKTRACE =
      ExceptionIncubatingAttributes.EXCEPTION_STACKTRACE;
  public static final AttributeKey<String> EXCEPTION_TYPE =
      ExceptionIncubatingAttributes.EXCEPTION_TYPE;

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
