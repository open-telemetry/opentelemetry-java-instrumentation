package io.opentelemetry.instrumentation.api.incubator.semconv.genai.messages;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.google.auto.value.AutoValue;
import javax.annotation.Nullable;

/**
 * Represents a tool call requested by the model.
 */
@AutoValue
@JsonClassDescription("Tool call request part")
public abstract class ToolCallRequestPart implements MessagePart {

  @JsonProperty(required = true, value = "type")
  @JsonPropertyDescription("The type of the content captured in this part")
  public abstract String getType();

  @JsonProperty(value = "id")
  @JsonPropertyDescription("Unique identifier for the tool call")
  @Nullable
  public abstract String getId();

  @JsonProperty(required = true, value = "name")
  @JsonPropertyDescription("Name of the tool")
  public abstract String getName();

  @JsonProperty(value = "arguments")
  @JsonPropertyDescription("Arguments for the tool call")
  @Nullable
  public abstract Object getArguments();

  public static ToolCallRequestPart create(String name) {
    return new AutoValue_ToolCallRequestPart("tool_call", null, name, null);
  }

  public static ToolCallRequestPart create(String id, String name, Object arguments) {
    return new AutoValue_ToolCallRequestPart("tool_call", id, name, arguments);
  }
}
