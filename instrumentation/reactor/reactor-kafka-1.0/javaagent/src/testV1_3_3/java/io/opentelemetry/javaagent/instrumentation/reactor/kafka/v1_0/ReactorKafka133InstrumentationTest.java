/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactor.kafka.v1_0;

import org.junit.jupiter.api.Test;

class ReactorKafka133InstrumentationTest extends AbstractReactorKafkaTest {

  @Test
  void testReceive() {
    testSingleRecordProcess(recordConsumer -> receiver.receive(1).subscribe(recordConsumer));
  }

  @Test
  void testReceiveAutoAck() {
    testSingleRecordProcess(
        recordConsumer ->
            receiver.receiveAutoAck(1).subscribe(records -> records.subscribe(recordConsumer)));
  }

  @Test
  void testReceiveAtMostOnce() {
    testSingleRecordProcess(
        recordConsumer -> receiver.receiveAtmostOnce(1).subscribe(recordConsumer));
  }
}
