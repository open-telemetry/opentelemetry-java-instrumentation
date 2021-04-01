/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v1_11;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class AwsSdkInstrumentationModule extends InstrumentationModule {
  public AwsSdkInstrumentationModule() {
    super("aws-sdk", "aws-sdk-1.11");
  }

  @Override
  public String[] additionalHelperClassNames() {
    return new String[] {"io.opentelemetry.extension.aws.AwsXrayPropagator"};
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new AwsClientInstrumentation(),
        new AwsHttpClientInstrumentation(),
        new RequestExecutorInstrumentation());
  }
}
