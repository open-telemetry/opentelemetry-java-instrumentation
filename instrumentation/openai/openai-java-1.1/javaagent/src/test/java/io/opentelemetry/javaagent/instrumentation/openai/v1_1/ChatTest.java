/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.openai.v1_1;

import com.openai.client.OpenAIClient;
import io.opentelemetry.instrumentation.openai.v1_1.AbstractChatTest;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.extension.RegisterExtension;

class ChatTest extends AbstractChatTest {

  @RegisterExtension
  private static final AgentInstrumentationExtension testing =
      AgentInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension getTesting() {
    return testing;
  }

  @Override
  protected OpenAIClient wrap(OpenAIClient client) {
    return client;
  }

  @Override
  protected final List<Consumer<SpanDataAssert>> maybeWithTransportSpan(
      Consumer<SpanDataAssert> span) {
    List<Consumer<SpanDataAssert>> result = new ArrayList<>();
    result.add(span);
    // Do a very simple assertion since the telemetry is not part of this library.
    result.add(s -> s.hasName("POST"));
    return result;
  }
}
