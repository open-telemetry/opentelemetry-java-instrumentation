package io.opentelemetry.instrumentation.api.incubator.semconv.genai.messages;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.google.auto.value.AutoValue;
import java.util.List;

/**
 * Represents an input message sent to the model.
 */
@AutoValue
@JsonClassDescription("Input message")
public abstract class InputMessage {

  @JsonProperty(required = true, value = "role")
  @JsonPropertyDescription("Role of the entity that created the message")
  public abstract String getRole();

  @JsonProperty(required = true, value = "parts")
  @JsonPropertyDescription("List of message parts that make up the message content")
  public abstract List<MessagePart> getParts();

  public static InputMessage create(String role, List<MessagePart> parts) {
    return new AutoValue_InputMessage(role, parts);
  }

  public static InputMessage create(Role role, List<MessagePart> parts) {
    return new AutoValue_InputMessage(role.getValue(), parts);
  }
}
