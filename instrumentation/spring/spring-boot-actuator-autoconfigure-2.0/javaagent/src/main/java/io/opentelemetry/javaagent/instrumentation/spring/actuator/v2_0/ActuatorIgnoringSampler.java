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

public class ActuatorIgnoringSampler implements Sampler {
  private final Sampler delegate;

  public ActuatorIgnoringSampler(Sampler delegate) {
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
    if (name.equals("ReadOperationHandler") || name.equals("OperationHandler")) {
      System.out.println("FILTERED");
      return SamplingResult.drop();
    }

    return Optional.ofNullable(attributes.get(SemanticAttributes.HTTP_TARGET))
        .filter(targetAttribute -> targetAttribute.startsWith("/actuator"))
        .map(t -> {
          System.out.println("FILTERED 2");
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
