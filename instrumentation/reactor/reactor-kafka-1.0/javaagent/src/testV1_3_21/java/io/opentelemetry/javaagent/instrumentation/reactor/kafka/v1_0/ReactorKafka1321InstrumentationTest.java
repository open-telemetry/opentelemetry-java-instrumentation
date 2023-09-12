/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactor.kafka.v1_0;

import org.junit.jupiter.api.Test;

class ReactorKafka1321InstrumentationTest extends AbstractReactorKafkaTest {

  @Test
  void receiveBatch() {
    testSingleRecordProcess(
        recordConsumer ->
            receiver
                .receiveBatch()
                .concatMap(r -> r)
                .doOnNext(r -> r.receiverOffset().acknowledge())
                .subscribe(recordConsumer));
  }

  @Test
  void receiveBatchWithSize() {
    testSingleRecordProcess(
        recordConsumer ->
            receiver
                .receiveBatch(1)
                .concatMap(r -> r)
                .doOnNext(r -> r.receiverOffset().acknowledge())
                .subscribe(recordConsumer));
  }
}
