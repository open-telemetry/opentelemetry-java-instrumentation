package datadog.trace.instrumentation.ratpack.impl;

import datadog.opentracing.scopemanager.ContextualScopeManager;
import io.opentracing.Scope;
import io.opentracing.ScopeManager;
import io.opentracing.util.GlobalTracer;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.asm.Advice;
import ratpack.exec.ExecStarter;
import ratpack.func.Action;
import ratpack.handling.HandlerDecorator;
import ratpack.registry.Registry;
import ratpack.registry.RegistrySpec;

@Slf4j
public class RatpackServerAdvice {
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
