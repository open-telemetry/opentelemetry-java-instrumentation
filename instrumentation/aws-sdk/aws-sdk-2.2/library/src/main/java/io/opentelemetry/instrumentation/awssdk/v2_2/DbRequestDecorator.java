/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import io.opentelemetry.api.trace.Span;
import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;

final class DbRequestDecorator implements SdkRequestDecorator {

  @Override
  public void decorate(Span span, SdkRequest sdkRequest, ExecutionAttributes attributes) {}
}
