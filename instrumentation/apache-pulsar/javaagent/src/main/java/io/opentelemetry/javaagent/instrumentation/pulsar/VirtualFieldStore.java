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

class VirtualFieldStore {
  private static final VirtualField<Message<?>, Context> MSG_FIELD =
      VirtualField.find(Message.class, Context.class);
  private static final VirtualField<Producer<?>, ClientEnhanceInfo> PRODUCER_FIELD =
      VirtualField.find(Producer.class, ClientEnhanceInfo.class);
  private static final VirtualField<Consumer<?>, ClientEnhanceInfo> CONSUMER_FIELD =
      VirtualField.find(Consumer.class, ClientEnhanceInfo.class);

  private VirtualFieldStore() {}


  static void inject(Message<?> instance, Context context) {
    MSG_FIELD.set(instance, context);
  }

  static Context extract(Message<?> instance) {
    Context ctx = MSG_FIELD.get(instance);
    return ctx == null ? Context.current() : ctx;
  }

  static void inject(Producer<?> instance, ClientEnhanceInfo info) {
    PRODUCER_FIELD.set(instance, info);
  }

  static ClientEnhanceInfo extract(Producer<?> instance) {
    return PRODUCER_FIELD.get(instance);
  }

  static void inject(Consumer<?> instance, ClientEnhanceInfo info) {
    CONSUMER_FIELD.set(instance, info);
  }

  static ClientEnhanceInfo extract(Consumer<?> instance) {
    return CONSUMER_FIELD.get(instance);
  }

}
