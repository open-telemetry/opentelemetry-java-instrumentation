/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.impl.TopicMessageImpl;

public class VirtualFieldStore {
  private static final VirtualField<Message<?>, Context> MSG_FIELD =
      VirtualField.find(Message.class, Context.class);
  private static final VirtualField<Producer<?>, String> PRODUCER_FIELD =
      VirtualField.find(Producer.class, String.class);
  private static final VirtualField<Consumer<?>, String> CONSUMER_FIELD =
      VirtualField.find(Consumer.class, String.class);

  private VirtualFieldStore() {}

  public static void inject(Message<?> instance, Context context) {
    if (instance instanceof TopicMessageImpl<?>) {
      TopicMessageImpl<?> topicMessage = (TopicMessageImpl<?>) instance;
      instance = topicMessage.getMessage();
    }
    if (instance != null) {
      MSG_FIELD.set(instance, context);
    }
  }

  public static void inject(Producer<?> instance, String serviceUrl) {
    PRODUCER_FIELD.set(instance, serviceUrl);
  }

  public static void inject(Consumer<?> instance, String serviceUrl) {
    CONSUMER_FIELD.set(instance, serviceUrl);
  }

  public static Context extract(Message<?> instance) {
    if (instance instanceof TopicMessageImpl<?>) {
      TopicMessageImpl<?> topicMessage = (TopicMessageImpl<?>) instance;
      instance = topicMessage.getMessage();
    }
    if (instance == null) {
      return Context.current();
    }
    Context ctx = MSG_FIELD.get(instance);
    return ctx == null ? Context.current() : ctx;
  }

  public static String extract(Producer<?> instance) {
    String brokerUrl = PRODUCER_FIELD.get(instance);
    return brokerUrl == null ? "unknown" : brokerUrl;
  }

  public static String extract(Consumer<?> instance) {
    String brokerUrl = CONSUMER_FIELD.get(instance);
    return brokerUrl == null ? "unknown" : brokerUrl;
  }
}
