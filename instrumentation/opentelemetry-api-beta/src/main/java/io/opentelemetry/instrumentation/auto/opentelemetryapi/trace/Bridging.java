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

package io.opentelemetry.instrumentation.auto.opentelemetryapi.trace;

import application.io.opentelemetry.common.AttributeValue;
import application.io.opentelemetry.common.Attributes;
import application.io.opentelemetry.common.ReadableKeyValuePairs.KeyValueConsumer;
import application.io.opentelemetry.trace.DefaultSpan;
import application.io.opentelemetry.trace.EndSpanOptions;
import application.io.opentelemetry.trace.Span;
import application.io.opentelemetry.trace.SpanContext;
import application.io.opentelemetry.trace.Status;
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

  public static Span toApplication(io.opentelemetry.trace.Span agentSpan) {
    if (!agentSpan.getContext().isValid()) {
      // no need to wrap
      return DefaultSpan.getInvalid();
    } else {
      return new ApplicationSpan(agentSpan);
    }
  }

  public static io.opentelemetry.trace.Span toAgentOrNull(Span applicationSpan) {
    if (!applicationSpan.getContext().isValid()) {
      // no need to wrap
      return io.opentelemetry.trace.DefaultSpan.getInvalid();
    } else if (applicationSpan instanceof ApplicationSpan) {
      return ((ApplicationSpan) applicationSpan).getAgentSpan();
    } else {
      return null;
    }
  }

  public static SpanContext toApplication(io.opentelemetry.trace.SpanContext agentContext) {
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

  public static io.opentelemetry.trace.SpanContext toAgent(SpanContext applicationContext) {
    if (applicationContext.isRemote()) {
      return io.opentelemetry.trace.SpanContext.createFromRemoteParent(
          applicationContext.getTraceIdAsHexString(),
          applicationContext.getSpanIdAsHexString(),
          applicationContext.getTraceFlags(),
          toAgent(applicationContext.getTraceState()));
    } else {
      return io.opentelemetry.trace.SpanContext.create(
          applicationContext.getTraceIdAsHexString(),
          applicationContext.getSpanIdAsHexString(),
          applicationContext.getTraceFlags(),
          toAgent(applicationContext.getTraceState()));
    }
  }

  public static io.opentelemetry.common.Attributes toAgent(Attributes applicationAttributes) {
    final io.opentelemetry.common.Attributes.Builder agentAttributes =
        io.opentelemetry.common.Attributes.newBuilder();
    applicationAttributes.forEach(
        new KeyValueConsumer<String, AttributeValue>() {
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
      AttributeValue applicationValue) {
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

  public static io.opentelemetry.trace.Status toAgentOrNull(Status applicationStatus) {
    io.opentelemetry.trace.Status.CanonicalCode agentCanonicalCode;
    try {
      agentCanonicalCode =
          io.opentelemetry.trace.Status.CanonicalCode.valueOf(
              applicationStatus.getCanonicalCode().name());
    } catch (IllegalArgumentException e) {
      log.debug(
          "unexpected status canonical code: {}", applicationStatus.getCanonicalCode().name());
      return null;
    }
    return agentCanonicalCode.toStatus().withDescription(applicationStatus.getDescription());
  }

  public static io.opentelemetry.trace.Span.Kind toAgentOrNull(Span.Kind applicationSpanKind) {
    try {
      return io.opentelemetry.trace.Span.Kind.valueOf(applicationSpanKind.name());
    } catch (IllegalArgumentException e) {
      log.debug("unexpected span kind: {}", applicationSpanKind.name());
      return null;
    }
  }

  public static io.opentelemetry.trace.EndSpanOptions toAgent(
      EndSpanOptions applicationEndSpanOptions) {
    return io.opentelemetry.trace.EndSpanOptions.builder()
        .setEndTimestamp(applicationEndSpanOptions.getEndTimestamp())
        .build();
  }

  private static TraceState toApplication(io.opentelemetry.trace.TraceState agentTraceState) {
    TraceState.Builder applicationTraceState = TraceState.builder();
    for (io.opentelemetry.trace.TraceState.Entry entry : agentTraceState.getEntries()) {
      applicationTraceState.set(entry.getKey(), entry.getValue());
    }
    return applicationTraceState.build();
  }

  private static io.opentelemetry.trace.TraceState toAgent(TraceState applicationTraceState) {
    io.opentelemetry.trace.TraceState.Builder agentTraceState =
        io.opentelemetry.trace.TraceState.builder();
    for (TraceState.Entry entry : applicationTraceState.getEntries()) {
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
