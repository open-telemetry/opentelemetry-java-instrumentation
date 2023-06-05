/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactor.kafka.v1_0;

import org.junit.jupiter.api.Test;

class ReactorKafkaInstrumentationTest extends AbstractReactorKafkaTest {

  @Test
  void testReceive() {
    testSingleRecordProcess(recordConsumer -> receiver.receive().subscribe(recordConsumer));
  }

  @Test
  void testReceiveAutoAck() {
    testSingleRecordProcess(
        recordConsumer ->
            receiver.receiveAutoAck().subscribe(records -> records.subscribe(recordConsumer)));
  }

  @Test
  void testReceiveAtMostOnce() {
    testSingleRecordProcess(
        recordConsumer -> receiver.receiveAtmostOnce().subscribe(recordConsumer));
  }
}
