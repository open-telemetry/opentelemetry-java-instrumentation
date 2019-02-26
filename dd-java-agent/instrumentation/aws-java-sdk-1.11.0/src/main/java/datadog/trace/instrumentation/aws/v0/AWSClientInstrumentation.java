package datadog.trace.instrumentation.aws.v0;

import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.declaresField;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.amazonaws.handlers.RequestHandler2;
import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import java.util.List;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * This instrumentation might work with versions before 1.11.0, but this was the first version that
 * is tested. It could possibly be extended earlier.
 */
@AutoService(Instrumenter.class)
public final class AWSClientInstrumentation extends Instrumenter.Default {

  public AWSClientInstrumentation() {
    super("aws-sdk");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.amazonaws.AmazonWebServiceClient")
        .and(declaresField(named("requestHandler2s")));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "datadog.trace.agent.decorator.BaseDecorator",
      "datadog.trace.agent.decorator.ClientDecorator",
      "datadog.trace.agent.decorator.HttpClientDecorator",
      packageName + ".AwsSdkClientDecorator",
      packageName + ".TracingRequestHandler",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(isConstructor(), AWSClientAdvice.class.getName());
  }

  public static class AWSClientAdvice {
    // Since we're instrumenting the constructor, we can't add onThrowable.
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void addHandler(
        @Advice.FieldValue("requestHandler2s") final List<RequestHandler2> handlers) {
      boolean hasDDHandler = false;
      for (final RequestHandler2 handler : handlers) {
        if (handler instanceof TracingRequestHandler) {
          hasDDHandler = true;
          break;
        }
      }
      if (!hasDDHandler) {
        handlers.add(TracingRequestHandler.INSTANCE);
      }
    }
  }
}
