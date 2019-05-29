package datadog.trace.instrumentation.glassfish;

import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Constants;
import datadog.trace.agent.tooling.Instrumenter;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

/**
 * This instrumenter prevents a mechanism from GlassFish classloader to produces a class not found
 * exception in our tracer. Link to the GH issue:
 * https://github.com/eclipse-ee4j/glassfish/issues/22566 If a class loading is attempted, as an
 * example, as a resource and is it not found, then it is blacklisted. Successive attempts to load a
 * class as a class (not a resource) will fail because the class is not even tried. We hook into the
 * blacklisting method to avoid specific namespaces to be blacklisted.
 */
@Slf4j
@AutoService(Instrumenter.class)
public final class GlassfishInstrumentation extends Instrumenter.Default {

  public GlassfishInstrumentation() {
    super("glassfish");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {Constants.class.getName()};
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return ElementMatchers.named(
        "com.sun.enterprise.v3.server.APIClassLoaderServiceImpl$APIClassLoader");
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod().and(named("addToBlackList")).and(takesArguments(1)),
        AvoidGlassFishBlacklistAdvice.class.getName());
  }

  public static class AvoidGlassFishBlacklistAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void preventBlacklistingOfTracerClasses(
        @Advice.Argument(value = 0, readOnly = false) String name) {
      for (String prefix : Constants.BOOTSTRAP_PACKAGE_PREFIXES) {
        if (name.startsWith(prefix)) {
          name = "__datadog_no_blacklist." + name;
          break;
        }
      }
    }
  }
}
