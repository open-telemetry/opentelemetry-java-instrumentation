package datadog.trace.instrumentation.aws.v2;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import io.opentracing.Scope;
import io.opentracing.util.GlobalTracer;
import java.util.Collections;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import software.amazon.awssdk.core.internal.http.pipeline.stages.MakeAsyncHttpRequestStage;

/**
 * Separate instrumentation class to close aws request scope right after request has been submitted
 * for execution for Sync clients.
 */
@AutoService(Instrumenter.class)
public final class AwsHttpClientInstrumentation extends AbstractAwsClientInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return safeHasSuperType(
            named("software.amazon.awssdk.core.internal.http.pipeline.stages.MakeHttpRequestStage")
                .or(
                    named(
                        "software.amazon.awssdk.core.internal.http.pipeline.stages.MakeAsyncHttpRequestStage")))
        .and(not(isInterface()));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return Collections.singletonMap(
        isMethod().and(isPublic()).and(named("execute")), AwsHttpClientAdvice.class.getName());
  }

  public static class AwsHttpClientAdvice {
    // scope.close here doesn't actually close the span.

    /**
     * FIXME: This is a hack to prevent netty instrumentation from messing things up.
     *
     * <p>Currently netty instrumentation cannot handle way AWS SDK makes http requests. If AWS SDK
     * make a netty call with active scope then continuation will be created that would never be
     * closed preventing whole trace from reporting. This happens because netty switches channels
     * between connection and request stages and netty instrumentation cannot find continuation
     * stored in channel attributes.
     */
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static boolean methodEnter(@Advice.This final Object thiz) {
      if (thiz instanceof MakeAsyncHttpRequestStage) {
        final Scope scope = GlobalTracer.get().scopeManager().active();
        if (scope != null) {
          scope.close();
          return true;
        }
      }
      return false;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(@Advice.Enter final boolean scopeAlreadyClosed) {
      if (!scopeAlreadyClosed) {
        final Scope scope = GlobalTracer.get().scopeManager().active();
        if (scope != null) {
          scope.close();
        }
      }
    }

    /**
     * This is to make muzzle think we need TracingExecutionInterceptor to make sure we do not apply
     * this instrumentation when TracingExecutionInterceptor would not work.
     */
    public static void muzzleCheck() {
      TracingExecutionInterceptor.muzzleCheck();
    }
  }
}
