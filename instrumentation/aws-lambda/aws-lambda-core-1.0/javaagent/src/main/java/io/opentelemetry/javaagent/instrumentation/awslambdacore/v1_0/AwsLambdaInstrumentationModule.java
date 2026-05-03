/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awslambdacore.v1_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class AwsLambdaInstrumentationModule extends InstrumentationModule {

  public AwsLambdaInstrumentationModule() {
    super("aws-lambda-core", "aws-lambda-core-1.0", "aws-lambda");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // added in 1.0.0
    return hasClassesNamed("com.amazonaws.services.lambda.runtime.RequestHandler")
        // artifact presence gate (in which case aws-lambda-events-2.2 is used)
        // added in com.amazonaws:aws-lambda-java-events 2.2.0
        .and(not(hasClassesNamed("com.amazonaws.services.lambda.runtime.events.SQSEvent")));
  }

  @Override
  public boolean isHelperClass(String className) {
    return className.startsWith("io.opentelemetry.contrib.awsxray.");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new AwsLambdaRequestHandlerInstrumentation(),
        new AwsLambdaRequestStreamHandlerInstrumentation());
  }
}
