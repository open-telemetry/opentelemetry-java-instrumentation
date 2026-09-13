/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.PRODUCER;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.rocketmqclient.v4_8.base.BaseConf;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class RocketMqClientTest extends AbstractRocketMqClientTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Override
  InstrumentationExtension testing() {
    return testing;
  }

  @Override
  void configureMqProducer(DefaultMQProducer producer) {}

  @Override
  void configureMqPushConsumer(DefaultMQPushConsumer consumer) {}

  @Override
  boolean hasBatchCreateSpans() {
    return !Boolean.getBoolean("testBatchCreateSpansDisabled");
  }

  @Override
  boolean isJavaagent() {
    return true;
  }

  @Test
  void testBatchSendSuppression() throws Exception {
    assumeTrue(Boolean.getBoolean("testBatchSendSuppression"));
    String topic = BaseConf.initTopic();
    Instrumenter<String, Void> parentInstrumenter =
        Instrumenter.<String, Void>builder(
                testing().getOpenTelemetry(), "test-parent", request -> request)
            .buildInstrumenter(request -> CLIENT);
    Context parentContext = parentInstrumenter.start(Context.root(), "parent");
    try (Scope ignored = parentContext.makeCurrent()) {
      producer()
          .send(
              asList(
                  new Message(topic, "one".getBytes(UTF_8)),
                  new Message(topic, "two".getBytes(UTF_8))));
    } finally {
      parentInstrumenter.end(parentContext, "parent", null, null);
    }

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactlyInAnyOrder(
                    span -> span.hasName("parent").hasKind(CLIENT),
                    span -> span.hasName("create " + topic).hasKind(PRODUCER),
                    span -> span.hasName("create " + topic).hasKind(PRODUCER)));
  }
}
