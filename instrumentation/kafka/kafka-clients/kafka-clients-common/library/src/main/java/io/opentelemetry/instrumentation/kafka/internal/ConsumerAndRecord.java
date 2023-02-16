/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafka.internal;

import com.google.auto.value.AutoValue;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import javax.annotation.Nullable;
import org.apache.kafka.clients.consumer.Consumer;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
@AutoValue
public abstract class ConsumerAndRecord<R> {

  public static <R> ConsumerAndRecord<R> create(@Nullable Consumer<?, ?> consumer, R record) {
    return new AutoValue_ConsumerAndRecord<>(consumer, record);
  }

  @Nullable
  public abstract Consumer<?, ?> consumer();

  public abstract R record();

  private static final MethodHandle GET_GROUP_METADATA;
  private static final MethodHandle GET_GROUP_ID;

  static {
    MethodHandle getGroupMetadata;
    MethodHandle getGroupId;

    try {
      Class<?> consumerGroupMetadata =
          Class.forName("org.apache.kafka.clients.consumer.ConsumerGroupMetadata");

      MethodHandles.Lookup lookup = MethodHandles.publicLookup();
      getGroupMetadata =
          lookup.findVirtual(
              Consumer.class, "groupMetadata", MethodType.methodType(consumerGroupMetadata));
      getGroupId =
          lookup.findVirtual(consumerGroupMetadata, "groupId", MethodType.methodType(String.class));
    } catch (ClassNotFoundException | IllegalAccessException | NoSuchMethodException ignored) {
      getGroupMetadata = null;
      getGroupId = null;
    }

    GET_GROUP_METADATA = getGroupMetadata;
    GET_GROUP_ID = getGroupId;
  }

  @Nullable
  String consumerGroup() {
    if (GET_GROUP_METADATA == null || GET_GROUP_ID == null) {
      return null;
    }
    Consumer<?, ?> consumer = consumer();
    if (consumer == null) {
      return null;
    }
    try {
      Object metadata = GET_GROUP_METADATA.invoke(consumer);
      return (String) GET_GROUP_ID.invoke(metadata);
    } catch (Throwable e) {
      return null;
    }
  }
}
