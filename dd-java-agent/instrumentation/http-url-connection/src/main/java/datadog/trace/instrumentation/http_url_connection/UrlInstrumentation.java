package datadog.trace.instrumentation.http_url_connection;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.startSpan;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.*;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.api.Config;
import datadog.trace.api.DDSpanTypes;
import datadog.trace.api.DDTags;
import datadog.trace.bootstrap.InternalJarURLHandler;
import datadog.trace.bootstrap.instrumentation.api.AgentScope;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.bootstrap.instrumentation.api.Tags;
import java.net.URL;
import java.net.URLStreamHandler;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public class UrlInstrumentation extends Instrumenter.Default {

  public static final String COMPONENT = "UrlConnection";

  public UrlInstrumentation() {
    super("urlconnection", "httpurlconnection");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return is(URL.class);
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod().and(isPublic()).and(named("openConnection")),
        UrlInstrumentation.class.getName() + "$ConnectionErrorAdvice");
  }

  public static class ConnectionErrorAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void errorSpan(
        @Advice.This final URL url,
        @Advice.Thrown final Throwable throwable,
        @Advice.FieldValue("handler") final URLStreamHandler handler) {
      if (throwable != null) {
        // Various agent components end up calling `openConnection` indirectly
        // when loading classes. Avoid tracing these calls.
        final boolean disableTracing = handler instanceof InternalJarURLHandler;
        if (disableTracing) {
          return;
        }

        String protocol = url.getProtocol();
        protocol = protocol != null ? protocol : "url";

        final AgentSpan span =
            startSpan(protocol + ".request")
                .setTag(Tags.SPAN_KIND, Tags.SPAN_KIND_CLIENT)
                .setTag(DDTags.SPAN_TYPE, DDSpanTypes.HTTP_CLIENT)
                .setTag(Tags.COMPONENT, COMPONENT);

        try (final AgentScope scope = activateSpan(span, false)) {
          span.setTag(Tags.HTTP_URL, url.toString());
          span.setTag(Tags.PEER_PORT, url.getPort() == -1 ? 80 : url.getPort());
          span.setTag(Tags.PEER_HOSTNAME, url.getHost());
          if (Config.get().isHttpClientSplitByDomain()) {
            span.setTag(DDTags.SERVICE_NAME, url.getHost());
          }

          span.setError(true);
          span.addThrowable(throwable);
          span.finish();
        }
      }
    }
  }
}
