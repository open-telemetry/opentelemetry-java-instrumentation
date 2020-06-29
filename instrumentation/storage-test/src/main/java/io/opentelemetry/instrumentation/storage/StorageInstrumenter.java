package io.opentelemetry.instrumentation.storage;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.bootstrap.InstrumentationContext;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.auto.tooling.Instrumenter.Default;
import io.opentelemetry.context.Scope;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.Argument;
import net.bytebuddy.asm.Advice.Origin;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static io.opentelemetry.context.ContextUtils.withScopedContext;
import static io.opentelemetry.instrumentation.storage.StorageDecorator.DECORATE;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.named;

@AutoService(Instrumenter.class)
public class StorageInstrumenter extends Default {

  public StorageInstrumenter() {
    super("storage");
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return named("io.opentelemetry.benchmark.storage.StorageBenchmark");
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    Map result = new HashMap();
    result.put(named("contextInternal"), getClass().getName() + "$GrpcContextAdvice");
    result.put(named("fieldInternal"), getClass().getName() + "$FieldAdvice");
    return result;
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
        packageName + ".StorageDecorator",
    };
  }

  @Override
  public Map<String, String> contextStore() {
    return singletonMap("java.lang.String", Integer.class.getName());
  }

  public static class GrpcContextAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope start(
        @Origin("#m") final String method,
        @Argument(0) final int value) {

      return withScopedContext(DECORATE.attach(value + 1));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stop(@Advice.Enter final Scope scope) {
      scope.close();
    }
  }

  public static class FieldAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope start(
        @Origin("#m") final String method,
        @Argument(0) final int value) {

      InstrumentationContext.get(String.class, Integer.class).put(method, value + 1);

      return withScopedContext(DECORATE.attach(value + 1));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stop(@Advice.Enter final Scope scope) {
      scope.close();
    }
  }
}
