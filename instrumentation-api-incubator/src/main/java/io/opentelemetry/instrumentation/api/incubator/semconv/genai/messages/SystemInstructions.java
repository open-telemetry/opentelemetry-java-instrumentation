/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.genai.messages;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auto.value.AutoValue;
import java.util.List;

/** Represents the list of system instructions sent to the model. */
@AutoValue
@JsonClassDescription("System instructions")
public abstract class SystemInstructions {

  @JsonPropertyDescription("List of message parts that make up the system instructions")
  public abstract List<MessagePart> getParts();

  public static SystemInstructions create(List<MessagePart> parts) {
    return new AutoValue_SystemInstructions(parts);
  }

  public String toJsonString() {
    try {
      ObjectMapper mapper = new ObjectMapper();
      return mapper.writeValueAsString(this);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed to serialize SystemInstructions to JSON", e);
    }
  }
}
