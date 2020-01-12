package io.opentelemetry.auto.instrumentation.aws.v2;

import io.opentelemetry.auto.agent.tooling.Instrumenter;

public abstract class AbstractAwsClientInstrumentation extends Instrumenter.Default {
  private static final String INSTRUMENTATION_NAME = "aws-sdk";

  public AbstractAwsClientInstrumentation() {
    super(INSTRUMENTATION_NAME);
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "io.opentelemetry.auto.agent.decorator.BaseDecorator",
      "io.opentelemetry.auto.agent.decorator.ClientDecorator",
      "io.opentelemetry.auto.agent.decorator.HttpClientDecorator",
      packageName + ".AwsSdkClientDecorator",
      packageName + ".TracingExecutionInterceptor"
    };
  }
}
