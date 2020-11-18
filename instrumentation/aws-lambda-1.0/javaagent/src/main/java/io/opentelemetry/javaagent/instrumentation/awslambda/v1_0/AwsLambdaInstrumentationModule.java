/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awslambda.v1_0;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class AwsLambdaInstrumentationModule extends InstrumentationModule {
  public AwsLambdaInstrumentationModule() {
    super("aws-lambda", "aws-lambda-1.0");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".AwsLambdaInstrumentationHelper",
      "io.opentelemetry.instrumentation.awslambda.v1_0.AwsLambdaTracer",
      "io.opentelemetry.instrumentation.awslambda.v1_0.AwsLambdaMessageTracer",
      "io.opentelemetry.instrumentation.awslambda.v1_0.ParentContextExtractor",
      "io.opentelemetry.instrumentation.awslambda.v1_0.ParentContextExtractor$MapGetter",
      "io.opentelemetry.instrumentation.awslambda.v1_0.ParentContextExtractor$HeadersGetter"
    };
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new AwsLambdaRequestHandlerInstrumentation());
  }
}
