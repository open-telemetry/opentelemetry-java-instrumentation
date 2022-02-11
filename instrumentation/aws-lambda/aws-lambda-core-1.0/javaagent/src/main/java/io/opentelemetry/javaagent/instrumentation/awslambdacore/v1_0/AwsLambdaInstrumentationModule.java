/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awslambdacore.v1_0;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class AwsLambdaInstrumentationModule extends InstrumentationModule {
  public AwsLambdaInstrumentationModule() {
    super("aws-lambda", "aws-lambda-core-1.0");
  }

  @Override
  public boolean isHelperClass(String className) {
    return className.startsWith("io.opentelemetry.extension.aws.");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new AwsLambdaRequestHandlerInstrumentation());
  }
}
