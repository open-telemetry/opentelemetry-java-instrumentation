package datadog.trace.agent.tooling.compatibility;

import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.ExceptionHandlers;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.agent.tooling.Utils;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatchers;

@Slf4j
@AutoService(Instrumenter.class)
public final class GlassfishClassloaderBlacklistCompatibility implements Instrumenter {

  private static final String[] NON_BLACKLISTED_PREFIXES = {
    "io.opentracing",
  };

  @Override
  public AgentBuilder instrument(AgentBuilder agentBuilder) {

    return agentBuilder
        .type(ElementMatchers.nameEndsWith("InternalMyService"))
        .transform(
            new AgentBuilder.Transformer.ForAdvice()
                .include(Utils.getBootstrapProxy(), Utils.getAgentClassLoader())
                .withExceptionHandler(ExceptionHandlers.defaultExceptionHandler())
                .advice(nameStartsWith("internal"), AvoidGlassFishBlacklistAdvice.class.getName()));
  }

  public static class AvoidGlassFishBlacklistAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void preventBlacklistingOfTracerClasses(
        @Advice.Argument(value = 0, readOnly = false) String fullClassName) {
      for (String prefix : NON_BLACKLISTED_PREFIXES) {
        if (fullClassName.startsWith(prefix)) {
          if (log.isDebugEnabled()) {
            log.debug(
                "Prevented blacklisting of class {}. Stack trace is: \n{}",
                fullClassName,
                Utils.getStackTraceAsString());
          }
          fullClassName = "__datadog_no_blacklist." + fullClassName;
          break;
        }
      }
    }
  }
}
