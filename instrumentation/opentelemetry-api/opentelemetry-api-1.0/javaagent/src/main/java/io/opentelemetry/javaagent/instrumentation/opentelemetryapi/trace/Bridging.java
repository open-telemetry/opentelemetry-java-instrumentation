/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace;

import static java.util.logging.Level.FINE;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.TraceStateBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.ValueBridging;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

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
public class Bridging {

  private static final Logger logger = Logger.getLogger(Bridging.class.getName());

  public static application.io.opentelemetry.api.trace.Span toApplication(Span agentSpan) {
    if (!agentSpan.getSpanContext().isValid()) {
      // no need to wrap
      return application.io.opentelemetry.api.trace.Span.getInvalid();
    } else {
      return new ApplicationSpan(agentSpan);
    }
  }

  public static application.io.opentelemetry.api.trace.SpanContext toApplication(
      SpanContext agentContext) {
    if (agentContext.isRemote()) {
      return application.io.opentelemetry.api.trace.SpanContext.createFromRemoteParent(
          agentContext.getTraceId(),
          agentContext.getSpanId(),
          BridgedTraceFlags.fromAgent(agentContext.getTraceFlags()),
          toApplication(agentContext.getTraceState()));
    } else {
      return application.io.opentelemetry.api.trace.SpanContext.create(
          agentContext.getTraceId(),
          agentContext.getSpanId(),
          BridgedTraceFlags.fromAgent(agentContext.getTraceFlags()),
          toApplication(agentContext.getTraceState()));
    }
  }

  private static application.io.opentelemetry.api.trace.TraceState toApplication(
      TraceState agentTraceState) {
    application.io.opentelemetry.api.trace.TraceStateBuilder applicationTraceState =
        application.io.opentelemetry.api.trace.TraceState.builder();
    agentTraceState.forEach(applicationTraceState::put);
    return applicationTraceState.build();
  }

  public static Span toAgentOrNull(application.io.opentelemetry.api.trace.Span applicationSpan) {
    if (!applicationSpan.getSpanContext().isValid()) {
      // no need to wrap
      return Span.getInvalid();
    } else if (applicationSpan instanceof ApplicationSpan) {
      return ((ApplicationSpan) applicationSpan).getAgentSpan();
    } else {
      return null;
    }
  }

  public static SpanKind toAgentOrNull(
      application.io.opentelemetry.api.trace.SpanKind applicationSpanKind) {
    try {
      return SpanKind.valueOf(applicationSpanKind.name());
    } catch (IllegalArgumentException e) {
      logger.log(FINE, "unexpected span kind: {0}", applicationSpanKind.name());
      return null;
    }
  }

  public static SpanContext toAgent(
      application.io.opentelemetry.api.trace.SpanContext applicationContext) {
    if (applicationContext.isRemote()) {
      return SpanContext.createFromRemoteParent(
          applicationContext.getTraceId(),
          applicationContext.getSpanId(),
          BridgedTraceFlags.toAgent(applicationContext.getTraceFlags()),
          toAgent(applicationContext.getTraceState()));
    } else {
      return SpanContext.create(
          applicationContext.getTraceId(),
          applicationContext.getSpanId(),
          BridgedTraceFlags.toAgent(applicationContext.getTraceFlags()),
          toAgent(applicationContext.getTraceState()));
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes"}) // toAgent conversion uses raw AttributeKey
  public static Attributes toAgent(
      application.io.opentelemetry.api.common.Attributes applicationAttributes) {
    AttributesBuilder agentAttributes = Attributes.builder();
    applicationAttributes.forEach(
        (key, value) -> {
          AttributeKey agentKey = toAgent(key);
          if (agentKey != null) {
            if (key.getType().name().equals("VALUE")) {
              agentAttributes.put(agentKey, ValueBridging.toAgent(value));
            } else {
              agentAttributes.put(agentKey, value);
            }
          }
        });
    return agentAttributes.build();
  }

  // TODO optimize this by storing shaded AttributeKey inside of application AttributeKey instead of
  // creating every time
  @SuppressWarnings({"rawtypes"}) // conversion uses raw AttributeKey
  public static AttributeKey toAgent(
      application.io.opentelemetry.api.common.AttributeKey applicationKey) {
    switch (applicationKey.getType()) {
      case STRING:
        return AttributeKey.stringKey(applicationKey.getKey());
      case BOOLEAN:
        return AttributeKey.booleanKey(applicationKey.getKey());
      case LONG:
        return AttributeKey.longKey(applicationKey.getKey());
      case DOUBLE:
        return AttributeKey.doubleKey(applicationKey.getKey());
      case STRING_ARRAY:
        return AttributeKey.stringArrayKey(applicationKey.getKey());
      case BOOLEAN_ARRAY:
        return AttributeKey.booleanArrayKey(applicationKey.getKey());
      case LONG_ARRAY:
        return AttributeKey.longArrayKey(applicationKey.getKey());
      case DOUBLE_ARRAY:
        return AttributeKey.doubleArrayKey(applicationKey.getKey());
      default:
        if (applicationKey.getType().name().equals("VALUE")) {
          return AttributeKey.valueKey(applicationKey.getKey());
        }
        logger.log(FINE, "unexpected attribute key type: {0}", applicationKey.getType());
        return null;
    }
  }

  public static List<AttributeKey<?>> toAgent(
      List<application.io.opentelemetry.api.common.AttributeKey<?>> attributeKeys) {
    List<AttributeKey<?>> result = new ArrayList<>(attributeKeys.size());
    for (application.io.opentelemetry.api.common.AttributeKey<?> attributeKey : attributeKeys) {
      result.add(toAgent(attributeKey));
    }
    return result;
  }

  public static StatusCode toAgent(
      application.io.opentelemetry.api.trace.StatusCode applicationStatus) {
    StatusCode agentCanonicalCode;
    try {
      agentCanonicalCode = StatusCode.valueOf(applicationStatus.name());
    } catch (IllegalArgumentException e) {
      logger.log(FINE, "unexpected status canonical code: {0}", applicationStatus.name());
      return StatusCode.UNSET;
    }
    return agentCanonicalCode;
  }

  private static TraceState toAgent(
      application.io.opentelemetry.api.trace.TraceState applicationTraceState) {
    TraceStateBuilder agentTraceState = TraceState.builder();
    applicationTraceState.forEach(agentTraceState::put);
    return agentTraceState.build();
  }

  private Bridging() {}
}
