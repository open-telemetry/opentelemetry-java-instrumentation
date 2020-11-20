/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace;

import application.io.opentelemetry.api.common.AttributeConsumer;
import application.io.opentelemetry.api.common.AttributeKey;
import application.io.opentelemetry.api.common.Attributes;
import application.io.opentelemetry.api.trace.Span;
import application.io.opentelemetry.api.trace.SpanContext;
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
public class Bridging {

  private static final Logger log = LoggerFactory.getLogger(Bridging.class);

  // this is just an optimization to save some byte array allocations
  public static final ThreadLocal<byte[]> BUFFER = new ThreadLocal<>();

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
          agentContext.getTraceIdAsHexString(),
          agentContext.getSpanIdAsHexString(),
          agentContext.getTraceFlags(),
          toApplication(agentContext.getTraceState()));
    } else {
      return SpanContext.create(
          agentContext.getTraceIdAsHexString(),
          agentContext.getSpanIdAsHexString(),
          agentContext.getTraceFlags(),
          toApplication(agentContext.getTraceState()));
    }
  }

  private static TraceState toApplication(io.opentelemetry.api.trace.TraceState agentTraceState) {
    TraceStateBuilder applicationTraceState = TraceState.builder();
    agentTraceState.forEach(applicationTraceState::set);
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

  public static io.opentelemetry.api.trace.Span.Kind toAgentOrNull(Span.Kind applicationSpanKind) {
    try {
      return io.opentelemetry.api.trace.Span.Kind.valueOf(applicationSpanKind.name());
    } catch (IllegalArgumentException e) {
      log.debug("unexpected span kind: {}", applicationSpanKind.name());
      return null;
    }
  }

  public static io.opentelemetry.api.trace.SpanContext toAgent(SpanContext applicationContext) {
    if (applicationContext.isRemote()) {
      return io.opentelemetry.api.trace.SpanContext.createFromRemoteParent(
          applicationContext.getTraceIdAsHexString(),
          applicationContext.getSpanIdAsHexString(),
          applicationContext.getTraceFlags(),
          toAgent(applicationContext.getTraceState()));
    } else {
      return io.opentelemetry.api.trace.SpanContext.create(
          applicationContext.getTraceIdAsHexString(),
          applicationContext.getSpanIdAsHexString(),
          applicationContext.getTraceFlags(),
          toAgent(applicationContext.getTraceState()));
    }
  }

  public static io.opentelemetry.api.common.Attributes toAgent(Attributes applicationAttributes) {
    final io.opentelemetry.api.common.AttributesBuilder agentAttributes =
        io.opentelemetry.api.common.Attributes.builder();
    applicationAttributes.forEach(
        new AttributeConsumer() {
          @Override
          public <T> void accept(AttributeKey<T> key, T value) {
            io.opentelemetry.api.common.AttributeKey<T> agentKey = toAgent(key);
            if (agentKey != null) {
              agentAttributes.put(agentKey, value);
            }
          }
        });
    return agentAttributes.build();
  }

  // TODO optimize this by storing shaded AttributeKey inside of application AttributeKey instead of
  // creating every time
  @SuppressWarnings("unchecked")
  public static <T> io.opentelemetry.api.common.AttributeKey<T> toAgent(
      AttributeKey<T> applicationKey) {
    switch (applicationKey.getType()) {
      case STRING:
        return (io.opentelemetry.api.common.AttributeKey<T>)
            io.opentelemetry.api.common.AttributeKey.stringKey(applicationKey.getKey());
      case BOOLEAN:
        return (io.opentelemetry.api.common.AttributeKey<T>)
            io.opentelemetry.api.common.AttributeKey.booleanKey(applicationKey.getKey());
      case LONG:
        return (io.opentelemetry.api.common.AttributeKey<T>)
            io.opentelemetry.api.common.AttributeKey.longKey(applicationKey.getKey());
      case DOUBLE:
        return (io.opentelemetry.api.common.AttributeKey<T>)
            io.opentelemetry.api.common.AttributeKey.doubleKey(applicationKey.getKey());
      case STRING_ARRAY:
        return (io.opentelemetry.api.common.AttributeKey<T>)
            io.opentelemetry.api.common.AttributeKey.stringArrayKey(applicationKey.getKey());
      case BOOLEAN_ARRAY:
        return (io.opentelemetry.api.common.AttributeKey<T>)
            io.opentelemetry.api.common.AttributeKey.booleanArrayKey(applicationKey.getKey());
      case LONG_ARRAY:
        return (io.opentelemetry.api.common.AttributeKey<T>)
            io.opentelemetry.api.common.AttributeKey.longArrayKey(applicationKey.getKey());
      case DOUBLE_ARRAY:
        return (io.opentelemetry.api.common.AttributeKey<T>)
            io.opentelemetry.api.common.AttributeKey.doubleArrayKey(applicationKey.getKey());
      default:
        log.debug("unexpected attribute key type: {}", applicationKey.getType());
        return null;
    }
  }

  public static io.opentelemetry.api.trace.StatusCode toAgent(StatusCode applicationStatus) {
    io.opentelemetry.api.trace.StatusCode agentCanonicalCode;
    try {
      agentCanonicalCode = io.opentelemetry.api.trace.StatusCode.valueOf(applicationStatus.name());
    } catch (IllegalArgumentException e) {
      log.debug("unexpected status canonical code: {}", applicationStatus.name());
      return io.opentelemetry.api.trace.StatusCode.UNSET;
    }
    return agentCanonicalCode;
  }

  private static io.opentelemetry.api.trace.TraceState toAgent(TraceState applicationTraceState) {
    io.opentelemetry.api.trace.TraceStateBuilder agentTraceState =
        io.opentelemetry.api.trace.TraceState.builder();
    applicationTraceState.forEach(agentTraceState::set);
    return agentTraceState.build();
  }

  private static byte[] getBuffer() {
    byte[] bytes = BUFFER.get();
    if (bytes == null) {
      bytes = new byte[16];
      BUFFER.set(bytes);
    }
    return bytes;
  }
}
