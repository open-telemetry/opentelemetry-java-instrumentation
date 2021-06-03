/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer;

import io.opentelemetry.api.trace.SpanBuilder;

public interface AttributeBindings {
  boolean isEmpty();

  SpanBuilder apply(SpanBuilder builder, Object[] args);

  default AttributeBindings and(int index, AttributeBinding binding) {
    return new AttributeBindings() {
      @Override
      public boolean isEmpty() {
        return false;
      }

      @Override
      public SpanBuilder apply(SpanBuilder builder, Object[] args) {
        Object arg = args[index];
        if (arg != null) {
          return binding.apply(AttributeBindings.this.apply(builder, args), arg);
        } else {
          return AttributeBindings.this.apply(builder, args);
        }
      }
    };
  }

  static final AttributeBindings EMPTY =
      new AttributeBindings() {
        @Override
        public boolean isEmpty() {
          return true;
        }

        @Override
        public SpanBuilder apply(SpanBuilder builder, Object[] args) {
          return builder;
        }
      };
}
