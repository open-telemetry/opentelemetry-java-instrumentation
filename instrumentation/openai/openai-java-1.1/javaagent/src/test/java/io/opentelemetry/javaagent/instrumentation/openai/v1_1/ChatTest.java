/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.openai.v1_1;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_MESSAGE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_STACKTRACE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_TYPE;

import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.instrumentation.openai.v1_1.AbstractChatTest;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.LogRecordDataAssert;
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
  protected OpenAIClientAsync wrap(OpenAIClientAsync client) {
    return client;
  }

  @Override
  protected List<Consumer<SpanDataAssert>> maybeWithTransportSpan(Consumer<SpanDataAssert> span) {
    List<Consumer<SpanDataAssert>> result = new ArrayList<>();
    result.add(span);
    // Do a very simple assertion since the telemetry is not part of this library.
    result.add(s -> s.hasName("POST"));
    return result;
  }

  @Override
  protected List<Consumer<LogRecordDataAssert>> maybeWithTransportExceptionLog(
      Consumer<LogRecordDataAssert> logRecord) {
    List<Consumer<LogRecordDataAssert>> result = new ArrayList<>();
    result.add(
        transportLog ->
            transportLog
                .hasSeverity(Severity.WARN)
                .hasEventName("http.client.request.exception")
                .hasAttributesSatisfyingExactly(
                    equalTo(EXCEPTION_TYPE, "java.net.ConnectException"),
                    satisfies(
                        EXCEPTION_MESSAGE, val -> val.startsWith("Failed to connect to localhost")),
                    satisfies(EXCEPTION_STACKTRACE, val -> val.isNotNull())));
    result.add(logRecord);
    return result;
  }
}
