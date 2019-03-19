package datadog.trace.instrumentation.aws.v0;

import static datadog.trace.instrumentation.aws.v0.AwsSdkClientDecorator.DECORATE;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.declaresField;
import static net.bytebuddy.matcher.ElementMatchers.isAbstract;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.amazonaws.AmazonClientException;
import com.amazonaws.Request;
import com.amazonaws.handlers.RequestHandler2;
import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import io.opentracing.Scope;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * This is additional 'helper' to catch cases when HTTP request throws exception different from
 * {@link AmazonClientException}. In these cases {@link RequestHandler2#afterError} is not called.
 *
 * <p>FIXME: come up with tests for this - maybe some test that mimics timeout?
 */
@AutoService(Instrumenter.class)
public final class AWSHttpClientInstrumentation extends Instrumenter.Default {

  public AWSHttpClientInstrumentation() {
    super("aws-sdk");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.amazonaws.http.AmazonHttpClient.RequestExecutor")
        .and(declaresField(named("request")));
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
    return singletonMap(
        isMethod().and(not(isAbstract())).and(named("doExecute")),
        HttpClientAdvice.class.getName());
  }

  public static class HttpClientAdvice {
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.FieldValue("request") final Request<?> request,
        @Advice.Thrown final Throwable throwable) {
      if (throwable != null) {
        final Scope scope = request.getHandlerContext(TracingRequestHandler.SCOPE_CONTEXT_KEY);
        if (scope != null) {
          request.addHandlerContext(TracingRequestHandler.SCOPE_CONTEXT_KEY, null);
          DECORATE.onError(scope.span(), throwable);
          DECORATE.beforeFinish(scope.span());
          scope.close();
        }
      }
    }
  }
}
