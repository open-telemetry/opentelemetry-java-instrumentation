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

public class ActuatorFilteringSampler implements Sampler {
  private static final Logger logger = Logger.getLogger(ActuatorFilteringSampler.class.getName());

  private final Sampler delegate;

  public ActuatorFilteringSampler(Sampler delegate) {
    this.delegate = delegate;
  }

  @Override
  public SamplingResult shouldSample(
      Context parentContext,
      String traceId,
      String name,
      SpanKind spanKind,
      Attributes attributes,
      List<LinkData> parentLinks) {
    // checking the name isn't ideal but there is not a reliable alternative
    if (name.equals("ReadOperationHandler.handle") || name.equals("OperationHandler.handle")) {
      logger.fine("Span for actuator handler dropped");
      return SamplingResult.drop();
    }

    return Optional.ofNullable(attributes.get(SemanticAttributes.HTTP_TARGET))
        .filter(targetAttribute -> targetAttribute.startsWith("/actuator"))
        .map(t -> {
          logger.info("Span for actuator URL " + t + " dropped");
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
}
