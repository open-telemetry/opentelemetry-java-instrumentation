/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.instrumentation.api.instrumenter.db.DbAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessageOperation;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.rpc.RpcAttributesExtractor;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

class SpanKeyExtractorTest {

  @ParameterizedTest
  @ArgumentsSource(ClientSpanKeys.class)
  void shouldDetermineKeysForClientAttributesExtractors(
      AttributesExtractor<?, ?> attributesExtractor, SpanKey expectedSpanKey) {

    Set<SpanKey> spanKeys = SpanKeyExtractor.determineSpanKeys(singletonList(attributesExtractor));
    assertEquals(singleton(expectedSpanKey), spanKeys);
  }

  static final class ClientSpanKeys implements ArgumentsProvider {

    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
      return Stream.of(
          Arguments.of(mock(DbAttributesExtractor.class), SpanKey.DB_CLIENT),
          Arguments.of(mock(HttpClientAttributesExtractor.class), SpanKey.HTTP_CLIENT),
          Arguments.of(mock(RpcAttributesExtractor.class), SpanKey.RPC_CLIENT));
    }
  }

  @ParameterizedTest
  @ArgumentsSource(MessagingSpanKeys.class)
  void shouldDetermineKeysForMessagingAttributesExtractor(
      MessageOperation operation, SpanKey expectedSpanKey) {

    MessagingAttributesExtractor<?, ?> attributesExtractor =
        mock(MessagingAttributesExtractor.class);
    when(attributesExtractor.operation()).thenReturn(operation);

    Set<SpanKey> spanKeys = SpanKeyExtractor.determineSpanKeys(singletonList(attributesExtractor));

    assertEquals(singleton(expectedSpanKey), spanKeys);
  }

  static final class MessagingSpanKeys implements ArgumentsProvider {

    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
      return Stream.of(
          Arguments.of(MessageOperation.PROCESS, SpanKey.CONSUMER_PROCESS),
          Arguments.of(MessageOperation.RECEIVE, SpanKey.CONSUMER_RECEIVE),
          Arguments.of(MessageOperation.SEND, SpanKey.PRODUCER));
    }
  }
}
