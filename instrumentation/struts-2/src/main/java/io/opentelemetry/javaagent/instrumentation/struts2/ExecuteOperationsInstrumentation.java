package io.opentelemetry.javaagent.instrumentation.struts2;

import static io.opentelemetry.javaagent.tooling.ClassLoaderMatcher.hasClassesNamed;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import java.util.Map;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public class ExecuteOperationsInstrumentation extends Instrumenter.Default {

  public ExecuteOperationsInstrumentation() {
    super("struts-2");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {packageName + ".Struts2Tracer"};
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
    // Optimization for expensive typeMatcher.
    return hasClassesNamed("org.apache.struts2.dispatcher.ExecuteOperations");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.struts2.dispatcher.ExecuteOperations");
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    System.out.println(" -------------- someone wants to know my transformers! ---------------");
    return singletonMap(
        isMethod()
            .and(isPublic())
            .and(named("executeAction"))
            .and(takesArgument(0, named("javax.servlet.http.HttpServletRequest")))
            .and(takesArgument(1, named("javax.servlet.http.HttpServletResponse")))
            .and(takesArgument(2, named("org.apache.struts2.dispatcher.mapper.ActionMapping")))
            .and(takesArguments(3)),
        ExecuteActionAdvice.class.getName());
  }

}
