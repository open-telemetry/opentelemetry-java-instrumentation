package io.opentelemetry.instrumentation.api.incubator.semconv.genai.messages;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.google.auto.value.AutoValue;
import javax.annotation.Nullable;

/**
 * Represents a tool call result sent to the model or a built-in tool call outcome and details.
 */
@AutoValue
@JsonClassDescription("Tool call response part")
public abstract class ToolCallResponsePart implements MessagePart {

  @JsonProperty(required = true, value = "type")
  @JsonPropertyDescription("The type of the content captured in this part")
  public abstract String getType();

  @JsonProperty(value = "id")
  @JsonPropertyDescription("Unique tool call identifier")
  @Nullable
  public abstract String getId();

  @JsonProperty(required = true, value = "response")
  @JsonPropertyDescription("Tool call response")
  public abstract Object getResponse();

  public static ToolCallResponsePart create(Object response) {
    return new AutoValue_ToolCallResponsePart("tool_call_response", null, response);
  }

  public static ToolCallResponsePart create(String id, Object response) {
    return new AutoValue_ToolCallResponsePart("tool_call_response", id, response);
  }
}
