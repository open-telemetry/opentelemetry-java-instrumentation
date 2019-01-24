package datadog.trace.instrumentation.aws.v2;

import static datadog.trace.agent.tooling.ClassLoaderMatcher.classLoaderHasClassWithField;
import static datadog.trace.agent.tooling.ClassLoaderMatcher.classLoaderHasClasses;

import datadog.trace.agent.tooling.Instrumenter;
import net.bytebuddy.matcher.ElementMatcher;

public abstract class AbstractAwsClientInstrumentation extends Instrumenter.Default {
  private static final String INSTRUMENTATION_NAME = "aws-sdk";

  public AbstractAwsClientInstrumentation() {
    super(INSTRUMENTATION_NAME);
  }

  // this is required to make sure we do not apply instrumentation to versions before 2.2.0
  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
    return classLoaderHasClasses("software.amazon.awssdk.http.SdkHttpRequest$Builder")
        .and(
            classLoaderHasClassWithField(
                "software.amazon.awssdk.core.interceptor.SdkExecutionAttribute", "OPERATION_NAME"));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      AwsClientInstrumentation.class.getPackage().getName() + ".TracingExecutionInterceptor",
      AwsClientInstrumentation.class.getPackage().getName()
          + ".TracingExecutionInterceptor$InjectAdapter"
    };
  }
}
