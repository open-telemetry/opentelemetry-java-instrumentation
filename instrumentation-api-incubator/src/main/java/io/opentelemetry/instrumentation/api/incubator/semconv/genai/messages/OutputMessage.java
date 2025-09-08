package io.opentelemetry.instrumentation.api.incubator.semconv.genai.messages;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.google.auto.value.AutoValue;
import java.util.List;

@AutoValue
@JsonClassDescription("Output message")
public abstract class OutputMessage {

  @JsonProperty(required = true, value = "role")
  @JsonPropertyDescription("Role of response")
  public abstract String getRole();

  @JsonProperty(required = true, value = "parts")
  @JsonPropertyDescription("List of message parts that make up the message content")
  public abstract List<MessagePart> getParts();

  @JsonProperty(required = true, value = "finish_reason")
  @JsonPropertyDescription("Reason for finishing the generation")
  public abstract String getFinishReason();

  public static OutputMessage create(String role, List<MessagePart> parts, String finishReason) {
    return new AutoValue_OutputMessage(role, parts, finishReason);
  }

  public static OutputMessage create(Role role, List<MessagePart> parts, String finishReason) {
    return new AutoValue_OutputMessage(role.getValue(), parts, finishReason);
  }
}
