package datadog.trace.instrumentation.couchbase.client;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static io.opentracing.log.Fields.ERROR_OBJECT;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import com.couchbase.client.java.CouchbaseCluster;
import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.api.DDTags;
import datadog.trace.bootstrap.CallDepthThreadLocalMap;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import rx.Observable;
import rx.functions.Action0;
import rx.functions.Action1;

@AutoService(Instrumenter.class)
public class CouchbaseClientInstrumentation extends Instrumenter.Default {

  public CouchbaseClientInstrumentation() {
    super("couchbase");
  }

  @Override
  protected boolean defaultEnabled() {
    return false;
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(isInterface())
        .and(
            safeHasSuperType(
                named("com.couchbase.client.java.cluster.AsyncClusterManager")
                    .or(named("com.couchbase.client.java.bucket.AsyncBucketManager"))
                    .or(named("com.couchbase.client.java.AsyncCluster"))
                    .or(named("com.couchbase.client.java.AsyncBucket"))));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      getClass().getName() + "$TraceSpanStart",
      getClass().getName() + "$TraceSpanFinish",
      getClass().getName() + "$TraceSpanError",
    };
  }

  @Override
  public Map<ElementMatcher, String> transformers() {
    return Collections.<ElementMatcher, String>singletonMap(
        isMethod().and(isPublic()).and(returns(named("rx.Observable"))),
        CouchbaseClientAdvice.class.getName());
  }

  public static class CouchbaseClientAdvice {

    @Advice.OnMethodEnter
    public static int trackCallDepth() {
      return CallDepthThreadLocalMap.incrementCallDepth(CouchbaseCluster.class);
    }

    @Advice.OnMethodExit
    public static void subscribeResult(
        @Advice.Enter final int callDepth,
        @Advice.Origin final Method method,
        @Advice.Return(readOnly = false) Observable result) {
      if (callDepth > 0) {
        return;
      }
      CallDepthThreadLocalMap.reset(CouchbaseCluster.class);
      final AtomicReference<Span> spanRef = new AtomicReference<>();
      result =
          result
              .doOnSubscribe(new TraceSpanStart(method, spanRef))
              .doOnCompleted(new TraceSpanFinish(spanRef))
              .doOnError(new TraceSpanError(spanRef));
    }
  }

  public static class TraceSpanStart implements Action0 {
    private final Method method;
    private final AtomicReference<Span> spanRef;

    public TraceSpanStart(final Method method, final AtomicReference<Span> spanRef) {
      this.method = method;
      this.spanRef = spanRef;
    }

    @Override
    public void call() {
      final Class<?> declaringClass = method.getDeclaringClass();
      final String className =
          declaringClass.getSimpleName().replace("CouchbaseAsync", "").replace("DefaultAsync", "");
      final String resourceName = className + "." + method.getName();

      spanRef.set(
          GlobalTracer.get()
              .buildSpan("couchbase.call")
              .withTag(DDTags.SERVICE_NAME, "couchbase")
              .withTag(DDTags.RESOURCE_NAME, resourceName)
              .start());
    }
  }

  public static class TraceSpanFinish implements Action0 {
    private final AtomicReference<Span> spanRef;

    public TraceSpanFinish(final AtomicReference<Span> spanRef) {
      this.spanRef = spanRef;
    }

    @Override
    public void call() {
      final Span span = spanRef.getAndSet(null);

      if (span != null) {
        span.finish();
      }
    }
  }

  public static class TraceSpanError implements Action1<Throwable> {
    private final AtomicReference<Span> spanRef;

    public TraceSpanError(final AtomicReference<Span> spanRef) {
      this.spanRef = spanRef;
    }

    @Override
    public void call(final Throwable throwable) {
      final Span span = spanRef.getAndSet(null);
      if (span != null) {
        Tags.ERROR.set(span, true);
        span.log(Collections.singletonMap(ERROR_OBJECT, throwable));
        span.finish();
      }
    }
  }
}
