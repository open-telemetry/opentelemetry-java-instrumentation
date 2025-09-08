/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.genai.messages;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.google.auto.value.AutoValue;
import java.util.Map;

/**
 * Represents an arbitrary message part with any type and properties. This allows for extensibility
 * with custom message part types.
 */
@AutoValue
@JsonClassDescription("Generic part")
public abstract class GenericPart implements MessagePart {

  @JsonProperty(required = true, value = "type")
  @JsonPropertyDescription("The type of the content captured in this part")
  public abstract String getType();

  public static GenericPart create(String type) {
    return new AutoValue_GenericPart(type);
  }

  public static GenericPart create(String type, Map<String, Object> additionalProperties) {
    return new AutoValue_GenericPart(type);
  }
}
