package datadog.trace.instrumentation.ratpack;

import static datadog.trace.agent.tooling.ClassLoaderMatcher.classLoaderHasClassWithMethod;
import static net.bytebuddy.matcher.ElementMatchers.*;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.DDAdvice;
import datadog.trace.agent.tooling.HelperInjector;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.instrumentation.ratpack.impl.RatpackServerAdvice;
import java.lang.reflect.Modifier;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
@Slf4j
public final class RatpackInstrumentation extends Instrumenter.Configurable {

  static final String EXEC_NAME = "ratpack";

  static final HelperInjector ROOT_RATPACK_HELPER_INJECTOR =
      new HelperInjector(
          "datadog.opentracing.scopemanager.ContextualScopeManager",
          "datadog.opentracing.scopemanager.ScopeContext");

  private static final HelperInjector SERVER_REGISTRY_HELPER_INJECTOR =
      new HelperInjector(
          "datadog.trace.instrumentation.ratpack.impl.RatpackRequestExtractAdapter",
          "datadog.trace.instrumentation.ratpack.impl.RatpackScopeManager",
          "datadog.trace.instrumentation.ratpack.impl.RatpackServerAdvice$RatpackServerRegistryAdvice",
          "datadog.trace.instrumentation.ratpack.impl.TracingHandler");
  private static final HelperInjector EXEC_STARTER_HELPER_INJECTOR =
      new HelperInjector(
          "datadog.trace.instrumentation.ratpack.impl.RatpackServerAdvice$ExecStarterAdvice",
          "datadog.trace.instrumentation.ratpack.impl.RatpackServerAdvice$ExecStarterAction");

  static final TypeDescription.Latent ACTION_TYPE_DESCRIPTION =
      new TypeDescription.Latent("ratpack.func.Action", Modifier.PUBLIC, null);

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
        .transform(ROOT_RATPACK_HELPER_INJECTOR)
        .transform(SERVER_REGISTRY_HELPER_INJECTOR)
        .transform(
            DDAdvice.create()
                .advice(
                    isMethod().and(isStatic()).and(named("buildBaseRegistry")),
                    RatpackServerAdvice.RatpackServerRegistryAdvice.class.getName()))
        .asDecorator()
        .type(
            not(isInterface()).and(hasSuperType(named("ratpack.exec.ExecStarter"))),
            CLASSLOADER_CONTAINS_RATPACK_1_4_OR_ABOVE)
        .transform(ROOT_RATPACK_HELPER_INJECTOR)
        .transform(EXEC_STARTER_HELPER_INJECTOR)
        .transform(
            DDAdvice.create()
                .advice(
                    named("register").and(takesArguments(ACTION_TYPE_DESCRIPTION)),
                    RatpackServerAdvice.ExecStarterAdvice.class.getName()))
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
                    RatpackServerAdvice.ExecutionAdvice.class.getName()))
        .asDecorator();
  }
}
