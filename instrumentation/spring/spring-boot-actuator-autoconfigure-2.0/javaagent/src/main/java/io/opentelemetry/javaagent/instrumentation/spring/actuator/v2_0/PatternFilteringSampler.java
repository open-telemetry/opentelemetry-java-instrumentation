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
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class PatternFilteringSampler implements Sampler {
  private static final Logger logger = Logger.getLogger(PatternFilteringSampler.class.getName());

  private final Sampler delegate;
  private final Pattern nameFilterPattern;
  private final Pattern httpTargetFilterPattern;

  public PatternFilteringSampler(String nameFilterRegex, String httpTargetRegex, Sampler delegate) {
    this.delegate = delegate;
    this.nameFilterPattern = nameFilterRegex == null ? null : Pattern.compile(nameFilterRegex);
    this.httpTargetFilterPattern = httpTargetRegex == null ? null : Pattern.compile(httpTargetRegex);
  }

  @Override
  public SamplingResult shouldSample(
      Context parentContext,
      String traceId,
      String name,
      SpanKind spanKind,
      Attributes attributes,
      List<LinkData> parentLinks) {
    if (nameFilterPattern != null && nameFilterPattern.matcher(name).matches()) {
      logger.info("Span for name " + name + " dropped");
      return SamplingResult.drop();
    }

    String httpTarget = attributes.get(SemanticAttributes.HTTP_TARGET);

    if (httpTarget != null && httpTargetFilterPattern != null && httpTargetFilterPattern.matcher(httpTarget).matches()) {
      logger.info("Span for http target " + httpTarget + " dropped");
      return SamplingResult.drop();
    }

    return delegate.shouldSample(parentContext, traceId, name, spanKind, attributes, parentLinks);
  }

  @Override
  public String getDescription() {
    return this.getClass().getSimpleName();
  }
}
