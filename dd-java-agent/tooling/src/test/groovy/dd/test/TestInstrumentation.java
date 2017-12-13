package dd.test;

import static net.bytebuddy.matcher.ElementMatchers.*;

import dd.trace.DDAdvice;
import dd.trace.HelperInjector;
import dd.trace.Instrumenter;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;

public class TestInstrumentation implements Instrumenter {
  @Override
  public AgentBuilder instrument(AgentBuilder agentBuilder) {
    return agentBuilder
        .type(named(TestInstrumentation.class.getName() + "$ClassToInstrument"))
        .transform(
            DDAdvice.create()
                .advice(
                    isMethod().and(isPublic()).and(named("isInstrumented")),
                    AdviceClass.class.getName()))
        .asDecorator();
  }

  public static class ClassToInstrument {
    public static boolean isInstrumented() {
      return false;
    }
  }

  public static class AdviceClass {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void enterAdvice(@Advice.Return(readOnly = false) Boolean returnValue) {
      returnValue = HelperClass.returnTrue();
    }
  }

  public static class HelperClass {
    public static boolean returnTrue() {
      return true;
    }
  }
}
