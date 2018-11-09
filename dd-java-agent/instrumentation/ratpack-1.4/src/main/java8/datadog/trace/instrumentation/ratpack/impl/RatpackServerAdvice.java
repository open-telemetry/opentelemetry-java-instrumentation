package datadog.trace.instrumentation.ratpack.impl;

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
      registry =
          registry.join(
              Registry.builder()
                  .add(ScopeManager.class, GlobalTracer.get().scopeManager())
                  .add(HandlerDecorator.prepend(new TracingHandler()))
                  .build());
    }
  }

  public static class ExecStarterAdvice {
    @Advice.OnMethodEnter
    public static void addScopeToRegistry(
        @Advice.Argument(value = 0, readOnly = false) Action<? super RegistrySpec> action) {
      final Scope active = GlobalTracer.get().scopeManager().active();
      if (active != null) {
        action = new ExecStarterAction(active).append(action);
      }
    }
  }

  public static class ExecutionAdvice {
    @Advice.OnMethodExit
    public static void addScopeToRegistry(@Advice.Return final ExecStarter starter) {
      final Scope active = GlobalTracer.get().scopeManager().active();
      if (active != null) {
        starter.register(new ExecStarterAction(active));
      }
    }
  }

  public static class ExecStarterAction implements Action<RegistrySpec> {
    private final Scope active;

    public ExecStarterAction(final Scope active) {
      this.active = active;
    }

    @Override
    public void execute(final RegistrySpec spec) {
      if (active != null) {
        spec.add(active);
      }
    }
  }
}
