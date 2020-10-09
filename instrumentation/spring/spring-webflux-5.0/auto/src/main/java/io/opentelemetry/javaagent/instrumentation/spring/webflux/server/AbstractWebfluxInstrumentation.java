/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.spring.webflux.server;

import io.opentelemetry.javaagent.tooling.Instrumenter;

public abstract class AbstractWebfluxInstrumentation extends Instrumenter.Default {

  public AbstractWebfluxInstrumentation(String... additionalNames) {
    super("spring-webflux", additionalNames);
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".SpringWebfluxHttpServerTracer",
      packageName + ".AdviceUtils",
      packageName + ".AdviceUtils$SpanFinishingSubscriber",
      packageName + ".RouteOnSuccessOrError"
    };
  }
}
