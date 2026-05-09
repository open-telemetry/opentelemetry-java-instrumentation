/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rocketmqclient.v5_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.util.Map;
import javax.annotation.Nullable;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.apache.rocketmq.client.java.message.PublishingMessageImpl;

public class VirtualFieldStore {
  private static final VirtualField<PublishingMessageImpl, Context> MESSAGE_CONTEXT =
      VirtualField.find(PublishingMessageImpl.class, Context.class);
  private static final VirtualField<MessageView, Context> MESSAGE_VIEW_CONTEXT =
      VirtualField.find(MessageView.class, Context.class);
  private static final VirtualField<PublishingMessageImpl, Map<String, String>>
      MESSAGE_EXTRA_PROPERTIES = VirtualField.find(PublishingMessageImpl.class, Map.class);
  private static final VirtualField<MessageView, String> MESSAGE_CONSUMER_GROUP =
      VirtualField.find(MessageView.class, String.class);

  private VirtualFieldStore() {}

  @Nullable
  public static Context getContextByMessage(PublishingMessageImpl message) {
    return MESSAGE_CONTEXT.get(message);
  }

  @Nullable
  public static Context getContextByMessage(MessageView messageView) {
    return MESSAGE_VIEW_CONTEXT.get(messageView);
  }

  public static void setContextByMessage(PublishingMessageImpl message, Context context) {
    MESSAGE_CONTEXT.set(message, context);
  }

  public static void setContextByMessage(MessageView message, Context context) {
    MESSAGE_VIEW_CONTEXT.set(message, context);
  }

  @Nullable
  public static Map<String, String> getExtraPropertiesByMessage(PublishingMessageImpl message) {
    return MESSAGE_EXTRA_PROPERTIES.get(message);
  }

  public static void setExtraPropertiesByMessage(
      PublishingMessageImpl message, Map<String, String> extraProperties) {
    MESSAGE_EXTRA_PROPERTIES.set(message, extraProperties);
  }

  @Nullable
  public static String getConsumerGroupByMessage(MessageView messageView) {
    return MESSAGE_CONSUMER_GROUP.get(messageView);
  }

  public static void setConsumerGroupByMessage(MessageView messageView, String consumerGroup) {
    MESSAGE_CONSUMER_GROUP.set(messageView, consumerGroup);
  }
}
