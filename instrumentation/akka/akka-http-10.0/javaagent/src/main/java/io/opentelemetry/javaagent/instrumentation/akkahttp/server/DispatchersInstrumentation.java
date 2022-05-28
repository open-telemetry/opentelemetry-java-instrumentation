package io.opentelemetry.javaagent.instrumentation.akkahttp.server;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import akka.dispatch.DispatcherPrerequisites;
import akka.dispatch.Dispatchers;
import akka.dispatch.PinnedDispatcherConfigurator;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.HashMap;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class DispatchersInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("akka.dispatch.Dispatchers");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    // Instrument the Dispatchers to add a custom OTEL PinnedDispatcher
    // This is used to enforce a single thread per actor
    // This should allow ThreadLocals to function properly and stop scope leakage
    transformer.applyAdviceToMethod(isConstructor().and(takesArgument(1, named("akka.dispatch.DispatcherPrerequisites"))), this.getClass().getName() + "$DispatchersAdvice");
  }

  @SuppressWarnings("unused")
  public static class DispatchersAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void wrapHandler(@Advice.Argument(value = 1) DispatcherPrerequisites dispatcherPrerequisites,
                                   @Advice.This Dispatchers dispatchers) {
      HashMap<String, Object> threadPoolExecutorMap = new HashMap<>();
      threadPoolExecutorMap.put("allow-core-timeout", "off");

      HashMap<String, Object> dispatcherConfigMap = new HashMap<>();
      dispatcherConfigMap.put("id", AkkaHttpServerSingletons.OTEL_DISPATCHER_NAME);
      dispatcherConfigMap.put("type", "PinnedDispatcher");
      dispatcherConfigMap.put("executor", "thread-pool-executor");
      dispatcherConfigMap.put("thread-pool-executor", threadPoolExecutorMap);

      Config config = ConfigFactory.parseMap(dispatcherConfigMap).withFallback(dispatchers.defaultDispatcherConfig());
      dispatchers.registerConfigurator(AkkaHttpServerSingletons.OTEL_DISPATCHER_NAME, new PinnedDispatcherConfigurator(config, dispatcherPrerequisites));
    }
  }
}
