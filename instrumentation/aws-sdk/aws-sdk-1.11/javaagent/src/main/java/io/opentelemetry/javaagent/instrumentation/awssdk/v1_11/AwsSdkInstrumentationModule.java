/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v1_11;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class AwsSdkInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {
  public AwsSdkInstrumentationModule() {
    super("aws-sdk", "aws-sdk-1.11", "aws-sdk-1.11-core");
  }

  @Override
  public boolean isHelperClass(String className) {
    return className.startsWith("io.opentelemetry.contrib.awsxray.");
  }

  @Override
  public String getModuleGroup() {
    return "aws-sdk";
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new AwsClientInstrumentation(),
        new AwsHttpClientInstrumentation(),
        new RequestExecutorInstrumentation());
  }
}
