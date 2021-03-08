/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.aspects.async;

import io.opentelemetry.context.ContextKey;

final class MethodSpanStrategyContextKey {
  public static ContextKey<MethodSpanStrategy> KEY =
      ContextKey.named("opentelemetry-spring-autoconfigure-aspects-method-span-strategy");

  private MethodSpanStrategyContextKey() {}
}
