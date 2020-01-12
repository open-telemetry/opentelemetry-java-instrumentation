package io.opentelemetry.auto.instrumentation.tomcat;

import static io.opentelemetry.auto.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.instrumentation.api.AgentTracer;
import io.opentelemetry.auto.tooling.Constants;
import io.opentelemetry.auto.tooling.Instrumenter;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Instrument Tomcat's web app classloader so it loads agent bootstrap classes from parent
 * classloader. Without this change web apps get their own versions of agent classes leading to
 * there being multiple {@link AgentTracer}s in existence, some of them not configured properly.
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
        TomcatClassloadingInstrumentation.class.getName() + "$WebappClassLoaderAdvice");
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
