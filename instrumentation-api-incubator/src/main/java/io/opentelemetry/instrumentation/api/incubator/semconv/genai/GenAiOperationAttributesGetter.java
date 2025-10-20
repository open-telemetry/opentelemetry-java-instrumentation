package io.opentelemetry.instrumentation.api.incubator.semconv.genai;

public interface GenAiOperationAttributesGetter<REQUEST, RESPONSE> {

  String getOperationName(REQUEST request);

  @Nullable
  String getOperationTarget(REQUEST request);
}
