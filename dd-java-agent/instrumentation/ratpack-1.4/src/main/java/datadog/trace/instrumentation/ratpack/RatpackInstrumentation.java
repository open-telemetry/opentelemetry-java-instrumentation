package datadog.trace.instrumentation.ratpack;

import static datadog.trace.agent.tooling.ClassLoaderMatcher.classLoaderHasClassWithMethod;
import static net.bytebuddy.matcher.ElementMatchers.*;

import com.google.auto.service.AutoService;
import datadog.opentracing.scopemanager.ContextualScopeManager;
import datadog.trace.agent.tooling.DDAdvice;
import datadog.trace.agent.tooling.HelperInjector;
import datadog.trace.agent.tooling.Instrumenter;
import io.opentracing.Scope;
import io.opentracing.ScopeManager;
import io.opentracing.util.GlobalTracer;
import java.lang.reflect.Modifier;
import java.util.Collections;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import ratpack.exec.ExecStarter;
import ratpack.func.Action;
import ratpack.handling.HandlerDecorator;
import ratpack.registry.Registry;
import ratpack.registry.RegistrySpec;

@AutoService(Instrumenter.class)
@Slf4j
public final class RatpackInstrumentation extends Instrumenter.Configurable {

  static final String EXEC_NAME = "ratpack";
  private static final HelperInjector SERVER_REGISTRY_HELPER_INJECTOR =
      new HelperInjector(
          "datadog.trace.instrumentation.ratpack.RatpackScopeManager",
          "datadog.trace.instrumentation.ratpack.TracingHandler",
          "datadog.trace.instrumentation.ratpack.RatpackInstrumentation$RatpackServerRegistryAdvice");
  private static final HelperInjector EXEC_STARTER_HELPER_INJECTOR =
      new HelperInjector(
          "datadog.trace.instrumentation.ratpack.RatpackInstrumentation$ExecStarterAdvice",
          "datadog.trace.instrumentation.ratpack.RatpackInstrumentation$ExecStarterAction");

  static final TypeDescription.Latent ACTION_TYPE_DESCRIPTION =
      new TypeDescription.Latent(
          "ratpack.func.Action", Modifier.PUBLIC, null, Collections.emptyList());

  static final ElementMatcher.Junction.AbstractBase<ClassLoader>
      CLASSLOADER_CONTAINS_RATPACK_1_4_OR_ABOVE =
          classLoaderHasClassWithMethod("ratpack.path.PathBinding", "getDescription");

  public RatpackInstrumentation() {
    super(EXEC_NAME);
  }

  @Override
  protected boolean defaultEnabled() {
    return false;
  }

  @Override
  public AgentBuilder apply(final AgentBuilder agentBuilder) {

    return agentBuilder
        .type(
            named("ratpack.server.internal.ServerRegistry"),
            CLASSLOADER_CONTAINS_RATPACK_1_4_OR_ABOVE)
        .transform(SERVER_REGISTRY_HELPER_INJECTOR)
        .transform(
            DDAdvice.create()
                .advice(
                    isMethod().and(isStatic()).and(named("buildBaseRegistry")),
                    RatpackServerRegistryAdvice.class.getName()))
        .asDecorator()
        .type(
            not(isInterface()).and(hasSuperType(named("ratpack.exec.ExecStarter"))),
            CLASSLOADER_CONTAINS_RATPACK_1_4_OR_ABOVE)
        .transform(EXEC_STARTER_HELPER_INJECTOR)
        .transform(
            DDAdvice.create()
                .advice(
                    named("register").and(takesArguments(ACTION_TYPE_DESCRIPTION)),
                    ExecStarterAdvice.class.getName()))
        .asDecorator()
        .type(
            named("ratpack.exec.Execution")
                .or(not(isInterface()).and(hasSuperType(named("ratpack.exec.Execution")))),
            CLASSLOADER_CONTAINS_RATPACK_1_4_OR_ABOVE)
        .transform(EXEC_STARTER_HELPER_INJECTOR)
        .transform(
            DDAdvice.create()
                .advice(
                    named("fork").and(returns(named("ratpack.exec.ExecStarter"))),
                    ExecutionAdvice.class.getName()))
        .asDecorator();
  }

  public static class RatpackServerRegistryAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void injectTracing(@Advice.Return(readOnly = false) Registry registry) {
      RatpackScopeManager ratpackScopeManager = new RatpackScopeManager();
      // the value returned from ServerRegistry.buildBaseRegistry needs to be modified to add our
      // scope manager and handler decorator to the registry
      //noinspection UnusedAssignment
      registry =
          registry.join(
              Registry.builder()
                  .add(ScopeManager.class, ratpackScopeManager)
                  .add(HandlerDecorator.prepend(new TracingHandler()))
                  .build());

      if (GlobalTracer.isRegistered()) {
        if (GlobalTracer.get().scopeManager() instanceof ContextualScopeManager) {
          ((ContextualScopeManager) GlobalTracer.get().scopeManager())
              .addScopeContext(ratpackScopeManager);
        }
      } else {
        log.warn("No GlobalTracer registered");
      }
    }
  }

  public static class ExecStarterAdvice {
    @Advice.OnMethodEnter
    public static void addScopeToRegistry(
        @Advice.Argument(value = 0, readOnly = false) Action<? super RegistrySpec> action) {
      Scope active = GlobalTracer.get().scopeManager().active();
      if (active != null) {
        //noinspection UnusedAssignment
        action = new ExecStarterAction(active).append(action);
      }
    }
  }

  public static class ExecutionAdvice {
    @Advice.OnMethodExit
    public static void addScopeToRegistry(@Advice.Return ExecStarter starter) {
      Scope active = GlobalTracer.get().scopeManager().active();
      if (active != null) {
        starter.register(new ExecStarterAction(active));
      }
    }
  }

  public static class ExecStarterAction implements Action<RegistrySpec> {
    private final Scope active;

    @SuppressWarnings("WeakerAccess")
    public ExecStarterAction(Scope active) {
      this.active = active;
    }

    @Override
    public void execute(RegistrySpec spec) {
      if (active != null) {
        spec.add(active);
      }
    }
  }
}
