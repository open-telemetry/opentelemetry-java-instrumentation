package com.example.javaagent;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.sdk.trace.samplers.SamplingResult;
import java.util.List;

/**
 * This demo sampler filters out all internal spans whose name contains string "greeting".
 * <p>
 * See <a href="https://github.com/open-telemetry/opentelemetry-specification/blob/master/specification/trace/sdk.md#sampling">
 * OpenTelemetry Specification</a> for more information about span sampling.
 *
 * @see DemoSdkTracerProviderConfigurer
 */
public class DemoSampler implements Sampler {
  @Override
  public SamplingResult shouldSample(Context parentContext, String traceId, String name,
      Span.Kind spanKind, Attributes attributes, List<LinkData> parentLinks) {
    if (spanKind == Span.Kind.INTERNAL && name.contains("greeting")) {
      return SamplingResult.create(SamplingResult.Decision.DROP);
    } else {
      return SamplingResult.create(SamplingResult.Decision.RECORD_AND_SAMPLE);
    }
  }

  @Override
  public String getDescription() {
    return "DemoSampler";
  }
}
