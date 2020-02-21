package io.opentelemetry.auto.instrumentation.otapi;

import static io.opentelemetry.auto.instrumentation.otapi.Bridging.toShaded;
import static io.opentelemetry.auto.instrumentation.otapi.Bridging.toUnshaded;

import unshaded.io.opentelemetry.context.propagation.BinaryFormat;
import unshaded.io.opentelemetry.trace.SpanContext;

public class UnshadedBinaryFormat implements BinaryFormat<SpanContext> {

  private final io.opentelemetry.context.propagation.BinaryFormat<
          io.opentelemetry.trace.SpanContext>
      shadedBinaryFormat;

  public UnshadedBinaryFormat(
      final io.opentelemetry.context.propagation.BinaryFormat<io.opentelemetry.trace.SpanContext>
          shadedBinaryFormat) {
    this.shadedBinaryFormat = shadedBinaryFormat;
  }

  @Override
  public byte[] toByteArray(final SpanContext spanContext) {
    return shadedBinaryFormat.toByteArray(toShaded(spanContext));
  }

  @Override
  public SpanContext fromByteArray(final byte[] bytes) {
    return toUnshaded(shadedBinaryFormat.fromByteArray(bytes));
  }
}
