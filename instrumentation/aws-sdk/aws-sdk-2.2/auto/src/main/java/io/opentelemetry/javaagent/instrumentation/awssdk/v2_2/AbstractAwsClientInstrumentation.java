/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.awssdk.v2_2;

import io.opentelemetry.javaagent.tooling.Instrumenter;

public abstract class AbstractAwsClientInstrumentation extends Instrumenter.Default {
  private static final String INSTRUMENTATION_NAME = "aws-sdk";

  public AbstractAwsClientInstrumentation() {
    super(INSTRUMENTATION_NAME);
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".TracingExecutionInterceptor",
      packageName + ".TracingExecutionInterceptor$ScopeHolder",
      "io.opentelemetry.instrumentation.awssdk.v2_2.AwsSdk",
      "io.opentelemetry.instrumentation.awssdk.v2_2.AwsSdkHttpClientTracer",
      "io.opentelemetry.instrumentation.awssdk.v2_2.RequestType",
      "io.opentelemetry.instrumentation.awssdk.v2_2.SdkRequestDecorator",
      "io.opentelemetry.instrumentation.awssdk.v2_2.DbRequestDecorator",
      "io.opentelemetry.instrumentation.awssdk.v2_2.TracingExecutionInterceptor"
    };
  }
}
