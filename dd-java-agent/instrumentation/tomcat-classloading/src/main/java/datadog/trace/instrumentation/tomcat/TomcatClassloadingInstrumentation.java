package datadog.trace.instrumentation.tomcat;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Constants;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.api.GlobalTracer;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Instrument Tomcat's web app classloader so it loads Datadog's bootstrap classes from parent
 * classloader. Without this change web apps get their oen versions of Datadog's classes leading to
 * there being multiple {@link GlobalTracer}s in existance, some of them not configured properly.
 * This is really the same idea we have for OSGi and JBoss.
 */
@AutoService(Instrumenter.class)
public final class TomcatClassloadingInstrumentation extends Instrumenter.Default {
  public TomcatClassloadingInstrumentation() {
    super("tomcat-classloading");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return safeHasSuperType(named("org.apache.catalina.loader.WebappClassLoaderBase"));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {Constants.class.getName()};
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(named("filter"))
            .and(takesArgument(0, String.class))
            // Older versions have 1 argument method, newer versions have two arguments
            .and(takesArguments(2).or(takesArguments(1))),
        WebappClassLoaderAdvice.class.getName());
  }

  public static class WebappClassLoaderAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void methodExit(
        @Advice.Argument(0) final String name, @Advice.Return(readOnly = false) boolean result) {
      if (result) {
        return;
      }
      for (final String prefix : Constants.BOOTSTRAP_PACKAGE_PREFIXES) {
        if (name.startsWith(prefix)) {
          result = true;
          break;
        }
      }
    }
  }
}
