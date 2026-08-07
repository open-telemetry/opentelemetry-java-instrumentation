/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.springai.v1_0;

import static io.opentelemetry.javaagent.instrumentation.springai.v1_0.SpringAiMessageAttributes.serializeMessages;
import static io.opentelemetry.javaagent.instrumentation.springai.v1_0.SpringAiMessageAttributes.serializeResponses;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.javaagent.instrumentation.springai.v1_0.app.TestChatModel;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

class SpringAiMessageAttributesTest {

  @Test
  void serializesAndTruncatesInputMessageContent() {
    SpringAiMessageAttributes.SerializedMessages messages =
        serializeMessages(
            asList(new SystemMessage("line\nvalue"), new UserMessage("123456789😀")), 10);

    assertThat(messages.json())
        .isEqualTo(
            "[{\"role\":\"system\",\"content\":\"line\\nvalue\"},"
                + "{\"role\":\"user\",\"content\":\"123456789\"}]");
    assertThat(messages.truncated()).isTrue();
  }

  @Test
  void serializesAndTruncatesStreamedResponseContent() {
    ChatResponse response = new TestChatModel().call(new Prompt("ignored"));
    SpringAiMessageAttributes.SerializedMessages messages =
        serializeResponses(response, "first\n\"quoted\"", 11);

    assertThat(messages.json())
        .isEqualTo("[{\"role\":\"assistant\",\"content\":\"first\\n\\\"quot\"}]");
    assertThat(messages.truncated()).isTrue();
  }
}
