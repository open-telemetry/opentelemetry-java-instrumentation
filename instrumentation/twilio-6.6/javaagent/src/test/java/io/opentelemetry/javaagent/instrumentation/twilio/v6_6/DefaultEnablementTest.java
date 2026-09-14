/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.twilio.v6_6;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.twilio.http.Response;
import com.twilio.http.TwilioRestClient;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class DefaultEnablementTest {

  private static final boolean V3_PREVIEW =
      Boolean.getBoolean("otel.instrumentation.common.v3-preview");

  private static final String MESSAGE_RESPONSE_BODY =
      "{\"body\":\"Hello, World!\",\"sid\":\"MM123\",\"status\":\"sent\"}";

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void defaultEnablement() {
    TwilioRestClient twilioRestClient = mock(TwilioRestClient.class);
    when(twilioRestClient.getObjectMapper()).thenReturn(new ObjectMapper());
    when(twilioRestClient.request(any()))
        .thenReturn(
            new Response(new ByteArrayInputStream(MESSAGE_RESPONSE_BODY.getBytes(UTF_8)), 200));

    Message message =
        testing.runWithSpan(
            "parent",
            () ->
                Message.creator(
                        new PhoneNumber("+1 555 720 5913"),
                        new PhoneNumber("+1 555 555 5215"),
                        "Hello world!")
                    .create(twilioRestClient));

    assertThat(message.getBody()).isEqualTo("Hello, World!");
    if (V3_PREVIEW) {
      testing.waitAndAssertTraces(
          trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("parent")));
    } else {
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("parent"), span -> span.hasName("MessageCreator.create")));
    }
  }
}
