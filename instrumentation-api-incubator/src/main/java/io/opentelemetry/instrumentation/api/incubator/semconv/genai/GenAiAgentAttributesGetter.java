package io.opentelemetry.instrumentation.api.incubator.semconv.genai;

public interface GenAiAgentAttributesGetter<REQUEST, RESPONSE> {

  String getName(REQUEST request);

  @Nullable
  String getDescription(REQUEST request);

  @Nullable
  String getId(REQUEST request);

  @Nullable
  String getDataSourceId(REQUEST request);
}
