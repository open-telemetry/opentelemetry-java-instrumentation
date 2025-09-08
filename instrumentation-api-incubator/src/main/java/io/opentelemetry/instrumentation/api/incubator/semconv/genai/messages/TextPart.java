/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.genai.messages;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.google.auto.value.AutoValue;

/** Represents text content sent to or received from the model. */
@AutoValue
@JsonClassDescription("Text part")
public abstract class TextPart implements MessagePart {

  @JsonProperty(required = true, value = "type")
  @JsonPropertyDescription("The type of the content captured in this part")
  public abstract String getType();

  @JsonProperty(required = true, value = "content")
  @JsonPropertyDescription("Text content sent to or received from the model")
  public abstract String getContent();

  public static TextPart create(String content) {
    return new AutoValue_TextPart("text", content);
  }
}
