package datadog.trace.instrumentation.hystrix;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static datadog.trace.instrumentation.hystrix.HystrixDecorator.DECORATE;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import com.google.auto.service.AutoService;
import com.netflix.hystrix.HystrixInvokableInfo;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.instrumentation.rxjava.TracedOnSubscribe;
import io.opentracing.Span;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import rx.Observable;

@AutoService(Instrumenter.class)
public class HystrixInstrumentation extends Instrumenter.Default {

  private static final String OPERATION_NAME = "hystrix.cmd";

  public HystrixInstrumentation() {
    super("hystrix");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return safeHasSuperType(
        named("com.netflix.hystrix.HystrixCommand")
            .or(named("com.netflix.hystrix.HystrixObservableCommand")));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "rx.DDTracingUtil",
      "datadog.trace.agent.decorator.BaseDecorator",
      "datadog.trace.instrumentation.rxjava.SpanFinishingSubscription",
      "datadog.trace.instrumentation.rxjava.TracedSubscriber",
      "datadog.trace.instrumentation.rxjava.TracedOnSubscribe",
      packageName + ".HystrixDecorator",
      packageName + ".HystrixInstrumentation$HystrixOnSubscribe",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    final Map<ElementMatcher.Junction<MethodDescription>, String> transformers = new HashMap<>();
    transformers.put(
        named("getExecutionObservable").and(returns(named("rx.Observable"))),
        ExecuteAdvice.class.getName());
    transformers.put(
        named("getFallbackObservable").and(returns(named("rx.Observable"))),
        FallbackAdvice.class.getName());
    return transformers;
  }

  public static class ExecuteAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.This final HystrixInvokableInfo<?> command,
        @Advice.Return(readOnly = false) Observable result,
        @Advice.Thrown final Throwable throwable) {

      result = Observable.create(new HystrixOnSubscribe(result, command, "execute"));
    }
  }

  public static class FallbackAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.This final HystrixInvokableInfo<?> command,
        @Advice.Return(readOnly = false) Observable<?> result,
        @Advice.Thrown final Throwable throwable) {

      result = Observable.create(new HystrixOnSubscribe(result, command, "fallback"));
    }
  }

  public static class HystrixOnSubscribe extends TracedOnSubscribe {
    private final HystrixInvokableInfo<?> command;
    private final String methodName;

    public HystrixOnSubscribe(
        final Observable originalObservable,
        final HystrixInvokableInfo<?> command,
        final String methodName) {
      super(originalObservable, OPERATION_NAME, DECORATE);

      this.command = command;
      this.methodName = methodName;
    }

    @Override
    protected void afterStart(final Span span) {
      super.afterStart(span);

      DECORATE.onCommand(span, command, methodName);
    }
  }
}
