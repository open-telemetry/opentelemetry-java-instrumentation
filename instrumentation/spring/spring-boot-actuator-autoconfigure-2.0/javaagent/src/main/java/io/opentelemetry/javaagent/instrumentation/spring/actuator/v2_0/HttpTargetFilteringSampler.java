/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.actuator.v2_0;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.sdk.trace.samplers.SamplingResult;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class HttpTargetFilteringSampler implements Sampler {
  private static final Logger logger = Logger.getLogger(HttpTargetFilteringSampler.class.getName());

  private final Sampler delegate;
  private final Pattern pattern;

  public HttpTargetFilteringSampler(Sampler delegate, String regex) {
    this.delegate = delegate;
    this.pattern = Pattern.compile(regex);
  }

  @Override
  public SamplingResult shouldSample(
      Context parentContext,
      String traceId,
      String name,
      SpanKind spanKind,
      Attributes attributes,
      List<LinkData> parentLinks) {
    return Optional.ofNullable(attributes.get(SemanticAttributes.HTTP_TARGET))
        .filter(attr -> pattern.matcher(attr).matches())
        .map(t -> {
          logger.info("Span for http target " + t + " dropped");
          return SamplingResult.drop();
        })
        .orElseGet(
            () ->
                delegate.shouldSample(
                    parentContext, traceId, name, spanKind, attributes, parentLinks));
  }

  @Override
  public String getDescription() {
    return this.getClass().getSimpleName();
  }

  public static void main(String[] args) {
    System.out.println(Pattern.compile("\\/actuator.*").matcher("/actuator/health").matches());
  }
}
