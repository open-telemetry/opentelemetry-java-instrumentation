package io.opentelemetry.instrumentation.api.incubator.semconv.genai;

import io.opentelemetry.instrumentation.api.genai.messages.InputMessages;
import io.opentelemetry.instrumentation.api.genai.messages.OutputMessages;
import io.opentelemetry.instrumentation.api.genai.messages.SystemInstructions;
import io.opentelemetry.instrumentation.api.genai.messages.ToolDefinitions;
import javax.annotation.Nullable;

public interface GenAiMessagesProvider<REQUEST, RESPONSE> {

  @Nullable
  InputMessages inputMessages(REQUEST request, @Nullable RESPONSE response);

  @Nullable
  OutputMessages outputMessages(REQUEST request, @Nullable RESPONSE response);

  @Nullable
  SystemInstructions systemInstructions(REQUEST request, @Nullable RESPONSE response);

  @Nullable
  ToolDefinitions toolDefinitions(REQUEST request, @Nullable RESPONSE response);
}
