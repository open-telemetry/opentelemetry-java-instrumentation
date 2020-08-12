/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.auto.instrumentation.opentelemetryapi.trace;

import application.io.opentelemetry.common.AttributeValue;
import application.io.opentelemetry.common.Attributes;
import application.io.opentelemetry.common.ReadableKeyValuePairs.KeyValueConsumer;
import application.io.opentelemetry.trace.DefaultSpan;
import application.io.opentelemetry.trace.EndSpanOptions;
import application.io.opentelemetry.trace.Span;
import application.io.opentelemetry.trace.SpanContext;
import application.io.opentelemetry.trace.SpanId;
import application.io.opentelemetry.trace.Status;
import application.io.opentelemetry.trace.TraceFlags;
import application.io.opentelemetry.trace.TraceId;
import application.io.opentelemetry.trace.TraceState;
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

  public static Span toApplication(final io.opentelemetry.trace.Span agentSpan) {
    if (!agentSpan.getContext().isValid()) {
      // no need to wrap
      return DefaultSpan.getInvalid();
    } else {
      return new ApplicationSpan(agentSpan);
    }
  }

  public static io.opentelemetry.trace.Span toAgentOrNull(final Span applicationSpan) {
    if (!applicationSpan.getContext().isValid()) {
      // no need to wrap
      return io.opentelemetry.trace.DefaultSpan.getInvalid();
    } else if (applicationSpan instanceof ApplicationSpan) {
      return ((ApplicationSpan) applicationSpan).getAgentSpan();
    } else {
      return null;
    }
  }

  public static SpanContext toApplication(final io.opentelemetry.trace.SpanContext agentContext) {
    if (agentContext.isRemote()) {
      return SpanContext.createFromRemoteParent(
          toApplication(agentContext.getTraceId()),
          toApplication(agentContext.getSpanId()),
          toApplication(agentContext.getTraceFlags()),
          toApplication(agentContext.getTraceState()));
    } else {
      return SpanContext.create(
          toApplication(agentContext.getTraceId()),
          toApplication(agentContext.getSpanId()),
          toApplication(agentContext.getTraceFlags()),
          toApplication(agentContext.getTraceState()));
    }
  }

  public static io.opentelemetry.trace.SpanContext toAgent(final SpanContext applicationContext) {
    if (applicationContext.isRemote()) {
      return io.opentelemetry.trace.SpanContext.createFromRemoteParent(
          toAgent(applicationContext.getTraceId()),
          toAgent(applicationContext.getSpanId()),
          toAgent(applicationContext.getTraceFlags()),
          toAgent(applicationContext.getTraceState()));
    } else {
      return io.opentelemetry.trace.SpanContext.create(
          toAgent(applicationContext.getTraceId()),
          toAgent(applicationContext.getSpanId()),
          toAgent(applicationContext.getTraceFlags()),
          toAgent(applicationContext.getTraceState()));
    }
  }

  public static io.opentelemetry.common.Attributes toAgent(final Attributes applicationAttributes) {
    final io.opentelemetry.common.Attributes.Builder agentAttributes =
        io.opentelemetry.common.Attributes.newBuilder();
    applicationAttributes.forEach(
        new KeyValueConsumer<AttributeValue>() {
          @Override
          public void consume(String key, AttributeValue attributeValue) {
            io.opentelemetry.common.AttributeValue agentValue = toAgentOrNull(attributeValue);
            if (agentValue != null) {
              agentAttributes.setAttribute(key, agentValue);
            }
          }
        });
    return agentAttributes.build();
  }

  public static io.opentelemetry.common.AttributeValue toAgentOrNull(
      final AttributeValue applicationValue) {
    switch (applicationValue.getType()) {
      case STRING:
        return io.opentelemetry.common.AttributeValue.stringAttributeValue(
            applicationValue.getStringValue());
      case LONG:
        return io.opentelemetry.common.AttributeValue.longAttributeValue(
            applicationValue.getLongValue());
      case BOOLEAN:
        return io.opentelemetry.common.AttributeValue.booleanAttributeValue(
            applicationValue.getBooleanValue());
      case DOUBLE:
        return io.opentelemetry.common.AttributeValue.doubleAttributeValue(
            applicationValue.getDoubleValue());
      default:
        log.debug("unexpected attribute type: {}", applicationValue.getType());
        return null;
    }
  }

  public static io.opentelemetry.trace.Status toAgentOrNull(final Status applicationStatus) {
    io.opentelemetry.trace.Status.CanonicalCode agentCanonicalCode;
    try {
      agentCanonicalCode =
          io.opentelemetry.trace.Status.CanonicalCode.valueOf(
              applicationStatus.getCanonicalCode().name());
    } catch (final IllegalArgumentException e) {
      log.debug(
          "unexpected status canonical code: {}", applicationStatus.getCanonicalCode().name());
      return null;
    }
    return agentCanonicalCode.toStatus().withDescription(applicationStatus.getDescription());
  }

  public static io.opentelemetry.trace.Span.Kind toAgentOrNull(
      final Span.Kind applicationSpanKind) {
    try {
      return io.opentelemetry.trace.Span.Kind.valueOf(applicationSpanKind.name());
    } catch (final IllegalArgumentException e) {
      log.debug("unexpected span kind: {}", applicationSpanKind.name());
      return null;
    }
  }

  public static io.opentelemetry.trace.EndSpanOptions toAgent(
      final EndSpanOptions applicationEndSpanOptions) {
    return io.opentelemetry.trace.EndSpanOptions.builder()
        .setEndTimestamp(applicationEndSpanOptions.getEndTimestamp())
        .build();
  }

  private static TraceId toApplication(final io.opentelemetry.trace.TraceId agentTraceId) {
    byte[] bytes = getBuffer();
    agentTraceId.copyBytesTo(bytes, 0);
    return TraceId.fromBytes(bytes, 0);
  }

  private static SpanId toApplication(final io.opentelemetry.trace.SpanId agentSpanId) {
    byte[] bytes = getBuffer();
    agentSpanId.copyBytesTo(bytes, 0);
    return SpanId.fromBytes(bytes, 0);
  }

  private static TraceFlags toApplication(final io.opentelemetry.trace.TraceFlags agentTraceFlags) {
    return TraceFlags.fromByte(agentTraceFlags.getByte());
  }

  private static TraceState toApplication(final io.opentelemetry.trace.TraceState agentTraceState) {
    TraceState.Builder applicationTraceState = TraceState.builder();
    for (io.opentelemetry.trace.TraceState.Entry entry : agentTraceState.getEntries()) {
      applicationTraceState.set(entry.getKey(), entry.getValue());
    }
    return applicationTraceState.build();
  }

  private static io.opentelemetry.trace.TraceId toAgent(final TraceId applicationTraceId) {
    byte[] bytes = getBuffer();
    applicationTraceId.copyBytesTo(bytes, 0);
    return io.opentelemetry.trace.TraceId.fromBytes(bytes, 0);
  }

  private static io.opentelemetry.trace.SpanId toAgent(final SpanId applicationSpanId) {
    byte[] bytes = getBuffer();
    applicationSpanId.copyBytesTo(bytes, 0);
    return io.opentelemetry.trace.SpanId.fromBytes(bytes, 0);
  }

  private static io.opentelemetry.trace.TraceFlags toAgent(final TraceFlags applicationTraceFlags) {
    return io.opentelemetry.trace.TraceFlags.fromByte(applicationTraceFlags.getByte());
  }

  private static io.opentelemetry.trace.TraceState toAgent(final TraceState applicationTraceState) {
    io.opentelemetry.trace.TraceState.Builder agentTraceState =
        io.opentelemetry.trace.TraceState.builder();
    for (final TraceState.Entry entry : applicationTraceState.getEntries()) {
      agentTraceState.set(entry.getKey(), entry.getValue());
    }
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
