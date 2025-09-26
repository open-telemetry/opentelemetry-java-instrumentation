/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.genai.messages;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auto.value.AutoValue;
import java.util.ArrayList;
import java.util.List;

/** Represents a collection of input messages sent to the model. */
@AutoValue
public abstract class InputMessages {

  public abstract List<InputMessage> getMessages();

  public static InputMessages create() {
    return new AutoValue_InputMessages(new ArrayList<>());
  }

  public static InputMessages create(List<InputMessage> messages) {
    return new AutoValue_InputMessages(new ArrayList<>(messages));
  }

  public InputMessages append(InputMessage inputMessage) {
    List<InputMessage> messages = getMessages();
    messages.add(inputMessage);
    return this;
  }

  public String toJsonString() {
    try {
      ObjectMapper mapper = new ObjectMapper();
      return mapper.writeValueAsString(this);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed to serialize InputMessages to JSON", e);
    }
  }
}
