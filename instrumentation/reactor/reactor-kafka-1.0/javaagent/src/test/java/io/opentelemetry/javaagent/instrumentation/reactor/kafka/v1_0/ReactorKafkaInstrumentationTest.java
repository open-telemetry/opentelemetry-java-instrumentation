/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactor.kafka.v1_0;

import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanKind;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.kafka.sender.SenderRecord;

public class ReactorKafkaInstrumentationTest extends AbstractReactorKafkaTest {

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @Test
  void shouldCreateSpansForSingleRecordProcess() {
    Disposable disposable =
        receiver.receive().subscribe(record -> testing.runWithSpan("consumer", () -> {}));
    cleanup.deferCleanup(disposable::dispose);

    SenderRecord<String, String, Object> record =
        SenderRecord.create("testTopic", 0, null, "10", "testSpan", null);
    Flux<?> producer = sender.send(Flux.just(record));
    testing.runWithSpan("producer", () -> producer.blockLast(Duration.ofSeconds(30)));

    AtomicReference<SpanData> producerSpan = new AtomicReference<>();

    testing.waitAndAssertSortedTraces(
        orderByRootSpanKind(SpanKind.INTERNAL, SpanKind.CONSUMER),
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> span.hasName("producer"),
              span ->
                  span.hasName("testTopic send")
                      .hasKind(SpanKind.PRODUCER)
                      .hasParent(trace.getSpan(0))
                      .hasAttributesSatisfyingExactly(sendAttributes(record)));

          producerSpan.set(trace.getSpan(1));
        },
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("testTopic receive")
                        .hasKind(SpanKind.CONSUMER)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(receiveAttributes("testTopic")),
                span ->
                    span.hasName("testTopic process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(0))
                        .hasLinks(LinkData.create(producerSpan.get().getSpanContext()))
                        .hasAttributesSatisfyingExactly(processAttributes(record)),
                span -> span.hasName("consumer").hasParent(trace.getSpan(1))));
  }
}
