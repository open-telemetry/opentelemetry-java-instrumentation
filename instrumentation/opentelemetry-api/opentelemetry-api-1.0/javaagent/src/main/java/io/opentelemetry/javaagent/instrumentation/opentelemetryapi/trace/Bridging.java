/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace;

import application.io.opentelemetry.api.common.AttributeKey;
import application.io.opentelemetry.api.common.Attributes;
import application.io.opentelemetry.api.trace.Span;
import application.io.opentelemetry.api.trace.SpanContext;
import application.io.opentelemetry.api.trace.SpanKind;
import application.io.opentelemetry.api.trace.StatusCode;
import application.io.opentelemetry.api.trace.TraceState;
import application.io.opentelemetry.api.trace.TraceStateBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class translates between the (unshaded) OpenTelemetry API that the application brings and
 * the (shaded) OpenTelemetry API that is used by the agent.
 *
 * <p>"application.io.opentelemetry.*" refers to the (unshaded) OpenTelemetry API that the
 * application brings (as those references will be translated during the build to remove the
 * "application." prefix).
 *
 * <p>"io.opentelemetry.*" refers to the (shaded) OpenTelemetry API that is used by the agent (as
 * those references will later be shaded).
 *
 * <p>Also see comments in this module's gradle file.
 */
// Our convention for accessing agent package
@SuppressWarnings("UnnecessarilyFullyQualified")
public class Bridging {

  private static final Logger logger = LoggerFactory.getLogger(Bridging.class);

  public static Span toApplication(io.opentelemetry.api.trace.Span agentSpan) {
    if (!agentSpan.getSpanContext().isValid()) {
      // no need to wrap
      return Span.getInvalid();
    } else {
      return new ApplicationSpan(agentSpan);
    }
  }

  public static SpanContext toApplication(io.opentelemetry.api.trace.SpanContext agentContext) {
    if (agentContext.isRemote()) {
      return SpanContext.createFromRemoteParent(
          agentContext.getTraceId(),
          agentContext.getSpanId(),
          BridgedTraceFlags.fromAgent(agentContext.getTraceFlags()),
          toApplication(agentContext.getTraceState()));
    } else {
      return SpanContext.create(
          agentContext.getTraceId(),
          agentContext.getSpanId(),
          BridgedTraceFlags.fromAgent(agentContext.getTraceFlags()),
          toApplication(agentContext.getTraceState()));
    }
  }

  private static TraceState toApplication(io.opentelemetry.api.trace.TraceState agentTraceState) {
    TraceStateBuilder applicationTraceState = TraceState.builder();
    agentTraceState.forEach(applicationTraceState::put);
    return applicationTraceState.build();
  }

  public static io.opentelemetry.api.trace.Span toAgentOrNull(Span applicationSpan) {
    if (!applicationSpan.getSpanContext().isValid()) {
      // no need to wrap
      return io.opentelemetry.api.trace.Span.getInvalid();
    } else if (applicationSpan instanceof ApplicationSpan) {
      return ((ApplicationSpan) applicationSpan).getAgentSpan();
    } else {
      return null;
    }
  }

  public static io.opentelemetry.api.trace.SpanKind toAgentOrNull(SpanKind applicationSpanKind) {
    try {
      return io.opentelemetry.api.trace.SpanKind.valueOf(applicationSpanKind.name());
    } catch (IllegalArgumentException e) {
      logger.debug("unexpected span kind: {}", applicationSpanKind.name());
      return null;
    }
  }

  public static io.opentelemetry.api.trace.SpanContext toAgent(SpanContext applicationContext) {
    if (applicationContext.isRemote()) {
      return io.opentelemetry.api.trace.SpanContext.createFromRemoteParent(
          applicationContext.getTraceId(),
          applicationContext.getSpanId(),
          BridgedTraceFlags.toAgent(applicationContext.getTraceFlags()),
          toAgent(applicationContext.getTraceState()));
    } else {
      return io.opentelemetry.api.trace.SpanContext.create(
          applicationContext.getTraceId(),
          applicationContext.getSpanId(),
          BridgedTraceFlags.toAgent(applicationContext.getTraceFlags()),
          toAgent(applicationContext.getTraceState()));
    }
  }

  public static io.opentelemetry.api.common.Attributes toAgent(Attributes applicationAttributes) {
    io.opentelemetry.api.common.AttributesBuilder agentAttributes =
        io.opentelemetry.api.common.Attributes.builder();
    applicationAttributes.forEach(
        (key, value) -> {
          @SuppressWarnings({"unchecked", "rawtypes"})
          io.opentelemetry.api.common.AttributeKey agentKey = toAgent(key);
          if (agentKey != null) {
            agentAttributes.put(agentKey, value);
          }
        });
    return agentAttributes.build();
  }

  // TODO optimize this by storing shaded AttributeKey inside of application AttributeKey instead of
  // creating every time
  @SuppressWarnings({"rawtypes"})
  public static io.opentelemetry.api.common.AttributeKey toAgent(AttributeKey applicationKey) {
    switch (applicationKey.getType()) {
      case STRING:
        return io.opentelemetry.api.common.AttributeKey.stringKey(applicationKey.getKey());
      case BOOLEAN:
        return io.opentelemetry.api.common.AttributeKey.booleanKey(applicationKey.getKey());
      case LONG:
        return io.opentelemetry.api.common.AttributeKey.longKey(applicationKey.getKey());
      case DOUBLE:
        return io.opentelemetry.api.common.AttributeKey.doubleKey(applicationKey.getKey());
      case STRING_ARRAY:
        return io.opentelemetry.api.common.AttributeKey.stringArrayKey(applicationKey.getKey());
      case BOOLEAN_ARRAY:
        return io.opentelemetry.api.common.AttributeKey.booleanArrayKey(applicationKey.getKey());
      case LONG_ARRAY:
        return io.opentelemetry.api.common.AttributeKey.longArrayKey(applicationKey.getKey());
      case DOUBLE_ARRAY:
        return io.opentelemetry.api.common.AttributeKey.doubleArrayKey(applicationKey.getKey());
    }
    logger.debug("unexpected attribute key type: {}", applicationKey.getType());
    return null;
  }

  public static io.opentelemetry.api.trace.StatusCode toAgent(StatusCode applicationStatus) {
    io.opentelemetry.api.trace.StatusCode agentCanonicalCode;
    try {
      agentCanonicalCode = io.opentelemetry.api.trace.StatusCode.valueOf(applicationStatus.name());
    } catch (IllegalArgumentException e) {
      logger.debug("unexpected status canonical code: {}", applicationStatus.name());
      return io.opentelemetry.api.trace.StatusCode.UNSET;
    }
    return agentCanonicalCode;
  }

  private static io.opentelemetry.api.trace.TraceState toAgent(TraceState applicationTraceState) {
    io.opentelemetry.api.trace.TraceStateBuilder agentTraceState =
        io.opentelemetry.api.trace.TraceState.builder();
    applicationTraceState.forEach(agentTraceState::put);
    return agentTraceState.build();
  }
}
