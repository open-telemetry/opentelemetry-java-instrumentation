package datadog.trace.instrumentation.apachehttpasyncclient;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.http.Header;
import org.apache.http.HttpRequest;

/**
 * Early versions don't copy headers over on redirect. This instrumentation copies our headers over
 * manually. Inspired by
 * https://github.com/elastic/apm-agent-java/blob/master/apm-agent-plugins/apm-apache-httpclient-plugin/src/main/java/co/elastic/apm/agent/httpclient/ApacheHttpAsyncClientRedirectInstrumentation.java
 */
@AutoService(Instrumenter.class)
public class ApacheHttpClientRedirectInstrumentation extends Instrumenter.Default {

  public ApacheHttpClientRedirectInstrumentation() {
    super("httpasyncclient", "apache-httpasyncclient");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return safeHasSuperType(named("org.apache.http.client.RedirectStrategy"));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(named("getRedirect"))
            .and(takesArgument(0, named("org.apache.http.HttpRequest"))),
        ClientRedirectAdvice.class.getName());
  }

  public static class ClientRedirectAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    private static void onAfterExecute(
        @Advice.Argument(value = 0) final HttpRequest original,
        @Advice.Return(typing = Assigner.Typing.DYNAMIC) final HttpRequest redirect) {
      if (redirect == null) {
        return;
      }

      for (final Header header : original.getAllHeaders()) {
        final String name = header.getName().toLowerCase();
        if (name.startsWith("x-datadog-") || name.startsWith("x-b3-")) {
          if (!redirect.containsHeader(header.getName())) {
            redirect.setHeader(header.getName(), header.getValue());
          }
        }
      }
    }
  }
}
