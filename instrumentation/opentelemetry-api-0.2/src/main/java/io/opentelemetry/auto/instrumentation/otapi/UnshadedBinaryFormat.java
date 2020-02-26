/*
 * Copyright 2020, OpenTelemetry Authors
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
