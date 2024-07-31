/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.spring.smoketest;

import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import org.springframework.stereotype.Component;

@Component
public class SpringComponent {

  @SuppressWarnings("MethodCanBeStatic")
  @WithSpan
  public void withSpanMethod(@SpanAttribute String paramName) {}
}
