/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.springai.v1_0;

import static io.opentelemetry.javaagent.instrumentation.springai.v1_0.SpringAiMessageAttributes.serializeMessages;
import static io.opentelemetry.javaagent.instrumentation.springai.v1_0.SpringAiMessageAttributes.serializeResponses;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
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
    String messages =
        serializeMessages(
            asList(new SystemMessage("line\nvalue"), new UserMessage("123456789😀")), 10);

    assertThat(messages)
        .isEqualTo(
            "[{\"role\":\"system\",\"parts\":[{\"type\":\"text\",\"content\":\"line\\nvalue\"}]},"
                + "{\"role\":\"user\",\"parts\":[{\"type\":\"text\",\"content\":\"123456789\"}]}]");
  }

  @Test
  void serializesAndTruncatesStreamedResponseContent() {
    ChatResponse response = new TestChatModel().call(new Prompt("ignored"));
    String messages = serializeResponses(response, singletonList("first\n\"quoted\""), 11);

    assertThat(messages)
        .isEqualTo(
            "[{\"role\":\"assistant\",\"parts\":[{\"type\":\"text\",\"content\":\"first\\n\\\"quot\"}],\"finish_reason\":\"stop\"}]");
  }
}
