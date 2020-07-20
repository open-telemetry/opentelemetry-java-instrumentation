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

import io.opentelemetry.common.Attributes.Builder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import unshaded.io.opentelemetry.common.AttributeValue;
import unshaded.io.opentelemetry.common.Attributes;
import unshaded.io.opentelemetry.common.ReadableKeyValuePairs.KeyValueConsumer;
import unshaded.io.opentelemetry.trace.DefaultSpan;
import unshaded.io.opentelemetry.trace.EndSpanOptions;
import unshaded.io.opentelemetry.trace.Span;
import unshaded.io.opentelemetry.trace.SpanContext;
import unshaded.io.opentelemetry.trace.SpanId;
import unshaded.io.opentelemetry.trace.Status;
import unshaded.io.opentelemetry.trace.TraceFlags;
import unshaded.io.opentelemetry.trace.TraceId;
import unshaded.io.opentelemetry.trace.TraceState;

/**
 * This class translates between the (unshaded) OpenTelemetry API that the user brings and the
 * (shaded) OpenTelemetry API that is in the bootstrap class loader and used to report telemetry to
 * the agent.
 *
 * <p>"unshaded.io.opentelemetry.*" refers to the (unshaded) OpenTelemetry API that the user brings
 * (as those references will be translated during the build to remove the "unshaded." prefix).
 *
 * <p>"io.opentelemetry.*" refers to the (shaded) OpenTelemetry API that is in the bootstrap class
 * loader (as those references will later be shaded).
 *
 * <p>Also see comments in this module's gradle file.
 */
public class Bridging {

  private static final Logger log = LoggerFactory.getLogger(Bridging.class);

  // this is just an optimization to save some byte array allocations
  public static final ThreadLocal<byte[]> BUFFER = new ThreadLocal<>();

  public static Span toUnshaded(final io.opentelemetry.trace.Span shadedSpan) {
    if (!shadedSpan.getContext().isValid()) {
      // no need to wrap
      return DefaultSpan.getInvalid();
    } else {
      return new UnshadedSpan(shadedSpan);
    }
  }

  public static io.opentelemetry.trace.Span toShadedOrNull(final Span unshadedSpan) {
    if (!unshadedSpan.getContext().isValid()) {
      // no need to wrap
      return io.opentelemetry.trace.DefaultSpan.getInvalid();
    } else if (unshadedSpan instanceof UnshadedSpan) {
      return ((UnshadedSpan) unshadedSpan).getShadedSpan();
    } else {
      return null;
    }
  }

  public static SpanContext toUnshaded(final io.opentelemetry.trace.SpanContext shadedContext) {
    if (shadedContext.isRemote()) {
      return SpanContext.createFromRemoteParent(
          toUnshaded(shadedContext.getTraceId()),
          toUnshaded(shadedContext.getSpanId()),
          toUnshaded(shadedContext.getTraceFlags()),
          toUnshaded(shadedContext.getTraceState()));
    } else {
      return SpanContext.create(
          toUnshaded(shadedContext.getTraceId()),
          toUnshaded(shadedContext.getSpanId()),
          toUnshaded(shadedContext.getTraceFlags()),
          toUnshaded(shadedContext.getTraceState()));
    }
  }

  public static io.opentelemetry.trace.SpanContext toShaded(final SpanContext unshadedContext) {
    if (unshadedContext.isRemote()) {
      return io.opentelemetry.trace.SpanContext.createFromRemoteParent(
          toShaded(unshadedContext.getTraceId()),
          toShaded(unshadedContext.getSpanId()),
          toShaded(unshadedContext.getTraceFlags()),
          toShaded(unshadedContext.getTraceState()));
    } else {
      return io.opentelemetry.trace.SpanContext.create(
          toShaded(unshadedContext.getTraceId()),
          toShaded(unshadedContext.getSpanId()),
          toShaded(unshadedContext.getTraceFlags()),
          toShaded(unshadedContext.getTraceState()));
    }
  }

  public static io.opentelemetry.common.Attributes toShaded(final Attributes unshadedAttributes) {
    final Builder builder = io.opentelemetry.common.Attributes.newBuilder();
    unshadedAttributes.forEach(
        new KeyValueConsumer<AttributeValue>() {
          @Override
          public void consume(String key, AttributeValue attributeValue) {
            io.opentelemetry.common.AttributeValue shadedValue = toShadedOrNull(attributeValue);
            if (shadedValue != null) {
              builder.setAttribute(key, shadedValue);
            }
          }
        });
    return builder.build();
  }

  public static io.opentelemetry.common.AttributeValue toShadedOrNull(
      final AttributeValue unshadedValue) {
    switch (unshadedValue.getType()) {
      case STRING:
        return io.opentelemetry.common.AttributeValue.stringAttributeValue(
            unshadedValue.getStringValue());
      case LONG:
        return io.opentelemetry.common.AttributeValue.longAttributeValue(
            unshadedValue.getLongValue());
      case BOOLEAN:
        return io.opentelemetry.common.AttributeValue.booleanAttributeValue(
            unshadedValue.getBooleanValue());
      case DOUBLE:
        return io.opentelemetry.common.AttributeValue.doubleAttributeValue(
            unshadedValue.getDoubleValue());
      default:
        log.debug("unexpected attribute type: {}", unshadedValue.getType());
        return null;
    }
  }

  public static io.opentelemetry.trace.Status toShadedOrNull(final Status unshadedStatus) {
    io.opentelemetry.trace.Status.CanonicalCode canonicalCode;
    try {
      canonicalCode =
          io.opentelemetry.trace.Status.CanonicalCode.valueOf(
              unshadedStatus.getCanonicalCode().name());
    } catch (final IllegalArgumentException e) {
      log.debug("unexpected status canonical code: {}", unshadedStatus.getCanonicalCode().name());
      return null;
    }
    return canonicalCode.toStatus().withDescription(unshadedStatus.getDescription());
  }

  public static io.opentelemetry.trace.Span.Kind toShadedOrNull(final Span.Kind unshadedSpanKind) {
    try {
      return io.opentelemetry.trace.Span.Kind.valueOf(unshadedSpanKind.name());
    } catch (final IllegalArgumentException e) {
      log.debug("unexpected span kind: {}", unshadedSpanKind.name());
      return null;
    }
  }

  public static io.opentelemetry.trace.EndSpanOptions toShaded(
      final EndSpanOptions unshadedEndSpanOptions) {
    return io.opentelemetry.trace.EndSpanOptions.builder()
        .setEndTimestamp(unshadedEndSpanOptions.getEndTimestamp())
        .build();
  }

  private static TraceId toUnshaded(final io.opentelemetry.trace.TraceId shadedTraceId) {
    byte[] bytes = getBuffer();
    shadedTraceId.copyBytesTo(bytes, 0);
    return TraceId.fromBytes(bytes, 0);
  }

  private static SpanId toUnshaded(final io.opentelemetry.trace.SpanId shadedSpanId) {
    byte[] bytes = getBuffer();
    shadedSpanId.copyBytesTo(bytes, 0);
    return SpanId.fromBytes(bytes, 0);
  }

  private static TraceFlags toUnshaded(final io.opentelemetry.trace.TraceFlags shadedTraceFlags) {
    return TraceFlags.fromByte(shadedTraceFlags.getByte());
  }

  private static TraceState toUnshaded(final io.opentelemetry.trace.TraceState shadedTraceState) {
    TraceState.Builder builder = TraceState.builder();
    for (io.opentelemetry.trace.TraceState.Entry entry : shadedTraceState.getEntries()) {
      builder.set(entry.getKey(), entry.getValue());
    }
    return builder.build();
  }

  private static io.opentelemetry.trace.TraceId toShaded(final TraceId unshadedTraceId) {
    byte[] bytes = getBuffer();
    unshadedTraceId.copyBytesTo(bytes, 0);
    return io.opentelemetry.trace.TraceId.fromBytes(bytes, 0);
  }

  private static io.opentelemetry.trace.SpanId toShaded(final SpanId unshadedSpanId) {
    byte[] bytes = getBuffer();
    unshadedSpanId.copyBytesTo(bytes, 0);
    return io.opentelemetry.trace.SpanId.fromBytes(bytes, 0);
  }

  private static io.opentelemetry.trace.TraceFlags toShaded(final TraceFlags unshadedTraceFlags) {
    return io.opentelemetry.trace.TraceFlags.fromByte(unshadedTraceFlags.getByte());
  }

  private static io.opentelemetry.trace.TraceState toShaded(final TraceState unshadedTraceState) {
    io.opentelemetry.trace.TraceState.Builder builder = io.opentelemetry.trace.TraceState.builder();
    for (final TraceState.Entry entry : unshadedTraceState.getEntries()) {
      builder.set(entry.getKey(), entry.getValue());
    }
    return builder.build();
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
