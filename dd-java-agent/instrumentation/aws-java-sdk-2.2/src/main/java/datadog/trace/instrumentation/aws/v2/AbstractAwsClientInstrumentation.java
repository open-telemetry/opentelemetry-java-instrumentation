package datadog.trace.instrumentation.aws.v2;

import datadog.trace.agent.tooling.Instrumenter;

public abstract class AbstractAwsClientInstrumentation extends Instrumenter.Default {
  private static final String INSTRUMENTATION_NAME = "aws-sdk";

  public AbstractAwsClientInstrumentation() {
    super(INSTRUMENTATION_NAME);
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "datadog.trace.agent.decorator.BaseDecorator",
      "datadog.trace.agent.decorator.ClientDecorator",
      "datadog.trace.agent.decorator.HttpClientDecorator",
      packageName + ".AwsSdkClientDecorator",
      packageName + ".TracingExecutionInterceptor",
      packageName + ".TracingExecutionInterceptor$InjectAdapter"
    };
  }
}
