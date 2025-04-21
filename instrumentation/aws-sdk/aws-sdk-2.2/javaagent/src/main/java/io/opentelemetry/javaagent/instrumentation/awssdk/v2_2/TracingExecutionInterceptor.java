/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v2_2;

import io.opentelemetry.instrumentation.awssdk.v2_2.internal.AbstractTracingExecutionInterceptor;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;

/**
 * A {@link ExecutionInterceptor} for use as an SPI by the AWS SDK to automatically trace all
 * requests.
 */
public class TracingExecutionInterceptor extends AbstractTracingExecutionInterceptor {

  private final ExecutionInterceptor delegate =
      AwsSdkSingletons.telemetry().newExecutionInterceptor();

  @Override
  protected ExecutionInterceptor delegate() {
    return delegate;
  }
}
