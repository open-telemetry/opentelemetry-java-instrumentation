/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.apache.kafka.clients.consumer;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.Metadata;
import org.apache.kafka.clients.consumer.internals.ConsumerCoordinator;
import org.apache.kafka.clients.consumer.internals.ConsumerNetworkClient;
import org.apache.kafka.clients.consumer.internals.Fetcher;
import org.apache.kafka.clients.consumer.internals.SubscriptionState;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.metrics.Metrics;
import org.apache.kafka.common.utils.Time;

public final class TestKafkaConsumerFactory {

  @SuppressWarnings("unchecked")
  public static KafkaConsumer<String, String> create(ConsumerRecord<String, String> record) {
    TopicPartition partition = new TopicPartition(record.topic(), record.partition());
    ConsumerCoordinator coordinator = mock(ConsumerCoordinator.class);
    Fetcher<String, String> fetcher = mock(Fetcher.class);
    Time time = mock(Time.class);
    ConsumerNetworkClient client = mock(ConsumerNetworkClient.class);
    SubscriptionState subscriptions = mock(SubscriptionState.class);
    Metrics metrics = mock(Metrics.class);
    Metadata metadata = mock(Metadata.class);

    Map<TopicPartition, List<ConsumerRecord<String, String>>> fetchedRecords =
        singletonMap(partition, singletonList(record));
    when(fetcher.fetchedRecords()).thenReturn(fetchedRecords);
    when(time.milliseconds()).thenReturn(0L);
    when(subscriptions.hasNoSubscriptionOrUserAssignment()).thenReturn(false);
    when(subscriptions.hasAllFetchPositions()).thenReturn(true);
    when(metrics.metrics()).thenReturn(emptyMap());

    return new KafkaConsumer<>(
        "test",
        coordinator,
        null,
        null,
        fetcher,
        null,
        time,
        client,
        metrics,
        subscriptions,
        metadata,
        0,
        0);
  }

  private TestKafkaConsumerFactory() {}
}
