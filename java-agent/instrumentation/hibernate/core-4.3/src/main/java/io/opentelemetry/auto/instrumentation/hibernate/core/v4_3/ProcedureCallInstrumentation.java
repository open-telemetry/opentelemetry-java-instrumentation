package io.opentelemetry.auto.instrumentation.hibernate.core.v4_3;

import static io.opentelemetry.auto.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.bootstrap.ContextStore;
import io.opentelemetry.auto.bootstrap.InstrumentationContext;
import io.opentelemetry.auto.instrumentation.hibernate.SessionMethodUtils;
import io.opentelemetry.auto.instrumentation.hibernate.SessionState;
import io.opentelemetry.auto.tooling.Instrumenter;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.hibernate.procedure.ProcedureCall;

@AutoService(Instrumenter.class)
public class ProcedureCallInstrumentation extends Instrumenter.Default {

  public ProcedureCallInstrumentation() {
    super("hibernate", "hibernate-core");
  }

  @Override
  public Map<String, String> contextStore() {
    return singletonMap("org.hibernate.procedure.ProcedureCall", SessionState.class.getName());
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "io.opentelemetry.auto.instrumentation.hibernate.SessionMethodUtils",
      "io.opentelemetry.auto.instrumentation.hibernate.SessionState",
      "io.opentelemetry.auto.decorator.BaseDecorator",
      "io.opentelemetry.auto.decorator.ClientDecorator",
      "io.opentelemetry.auto.decorator.DatabaseClientDecorator",
      "io.opentelemetry.auto.decorator.OrmClientDecorator",
      "io.opentelemetry.auto.instrumentation.hibernate.HibernateDecorator",
    };
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(isInterface()).and(safeHasSuperType(named("org.hibernate.procedure.ProcedureCall")));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod().and(named("getOutputs")),
        ProcedureCallInstrumentation.class.getName() + "$ProcedureCallMethodAdvice");
  }

  public static class ProcedureCallMethodAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static SessionState startMethod(
        @Advice.This final ProcedureCall call, @Advice.Origin("#m") final String name) {

      final ContextStore<ProcedureCall, SessionState> contextStore =
          InstrumentationContext.get(ProcedureCall.class, SessionState.class);

      final SessionState state =
          SessionMethodUtils.startScopeFrom(
              contextStore, call, "hibernate.procedure." + name, call.getProcedureName(), true);
      return state;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void endMethod(
        @Advice.Enter final SessionState state, @Advice.Thrown final Throwable throwable) {
      SessionMethodUtils.closeScope(state, throwable, null);
    }
  }
}
