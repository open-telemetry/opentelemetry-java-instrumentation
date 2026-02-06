/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace;

import static java.util.logging.Level.FINE;

import application.io.opentelemetry.api.common.AttributeKey;
import application.io.opentelemetry.api.common.Attributes;
import application.io.opentelemetry.api.trace.Span;
import application.io.opentelemetry.api.trace.SpanContext;
import application.io.opentelemetry.api.trace.SpanKind;
import application.io.opentelemetry.api.trace.StatusCode;
import application.io.opentelemetry.api.trace.TraceState;
import application.io.opentelemetry.api.trace.TraceStateBuilder;
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
// Our convention for accessing agent package
@SuppressWarnings("UnnecessarilyFullyQualified")
public class Bridging {

  private static final Logger logger = Logger.getLogger(Bridging.class.getName());

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
      logger.log(FINE, "unexpected span kind: {0}", applicationSpanKind.name());
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

  @SuppressWarnings({"unchecked", "rawtypes"}) // toAgent conversion uses raw AttributeKey
  public static io.opentelemetry.api.common.Attributes toAgent(Attributes applicationAttributes) {
    io.opentelemetry.api.common.AttributesBuilder agentAttributes =
        io.opentelemetry.api.common.Attributes.builder();
    applicationAttributes.forEach(
        (key, value) -> {
          io.opentelemetry.api.common.AttributeKey agentKey = toAgent(key);
          if (agentKey != null) {
            // For VALUE type attributes, use helper to convert the Value object
            if (key.getType().name().equals("VALUE")) {
              Object agentValue = toAgentValueHelper(value);
              if (agentValue != null) {
                agentAttributes.put(agentKey, agentValue);
              }
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
      default:
        // Handle VALUE type added in 1.59.0 without direct enum reference for muzzle compatibility
        if (applicationKey.getType().name().equals("VALUE")) {
          return io.opentelemetry.api.common.AttributeKey.valueKey(applicationKey.getKey());
        }
        logger.log(FINE, "unexpected attribute key type: {0}", applicationKey.getType());
        return null;
    }
  }

  public static List<io.opentelemetry.api.common.AttributeKey<?>> toAgent(
      List<AttributeKey<?>> attributeKeys) {
    List<io.opentelemetry.api.common.AttributeKey<?>> result =
        new ArrayList<>(attributeKeys.size());
    for (AttributeKey<?> attributeKey : attributeKeys) {
      result.add(toAgent(attributeKey));
    }
    return result;
  }

  public static io.opentelemetry.api.trace.StatusCode toAgent(StatusCode applicationStatus) {
    io.opentelemetry.api.trace.StatusCode agentCanonicalCode;
    try {
      agentCanonicalCode = io.opentelemetry.api.trace.StatusCode.valueOf(applicationStatus.name());
    } catch (IllegalArgumentException e) {
      logger.log(FINE, "unexpected status canonical code: {0}", applicationStatus.name());
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

  // VALUE type bridging helper - loaded via reflection lazily from versioned module (1.59+)
  private static volatile ValueBridgingHelper valueBridgingHelper;
  private static volatile boolean valueBridgingHelperInitialized = false;

  private static ValueBridgingHelper getValueBridgingHelper() {
    if (!valueBridgingHelperInitialized) {
      synchronized (Bridging.class) {
        if (!valueBridgingHelperInitialized) {
          // this class is defined in opentelemetry-api-1.59
          valueBridgingHelper =
              getHelper(
                  "io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_59.ValueBridgingHelperImpl");
          valueBridgingHelperInitialized = true;
        }
      }
    }
    return valueBridgingHelper;
  }

  private static ValueBridgingHelper getHelper(String className) {
    try {
      Class<?> clazz = Class.forName(className);
      return (ValueBridgingHelper) clazz.getConstructor().newInstance();
    } catch (ClassNotFoundException
        | NoSuchMethodException
        | InstantiationException
        | IllegalAccessException
        | java.lang.reflect.InvocationTargetException exception) {
      return null;
    }
  }

  /** Converts application Value to agent Value using the helper if available. */
  public static Object toAgentValue(Object applicationValue) {
    ValueBridgingHelper helper = getValueBridgingHelper();
    if (helper != null) {
      return helper.toAgentValue(applicationValue);
    }
    // No helper available - skip VALUE attributes on older API versions
    logger.log(FINE, "VALUE attribute type requires SDK 1.59.0+, skipping");
    return null;
  }

  /** Converts application Value to agent Value using the helper if available (internal). */
  private static Object toAgentValueHelper(Object applicationValue) {
    return toAgentValue(applicationValue);
  }

  /** Interface for VALUE type bridging, implemented by versioned module. */
  public interface ValueBridgingHelper {
    Object toAgentValue(Object applicationValue);
  }

  private Bridging() {}
}
