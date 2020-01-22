package io.opentelemetry.auto.instrumentation.http_url_connection;

import static io.opentelemetry.auto.instrumentation.api.AgentTracer.activateSpan;
import static io.opentelemetry.auto.instrumentation.api.AgentTracer.startSpan;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.is;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.api.Config;
import io.opentelemetry.auto.api.MoreTags;
import io.opentelemetry.auto.api.SpanTypes;
import io.opentelemetry.auto.bootstrap.InternalJarURLHandler;
import io.opentelemetry.auto.instrumentation.api.AgentScope;
import io.opentelemetry.auto.instrumentation.api.AgentSpan;
import io.opentelemetry.auto.instrumentation.api.Tags;
import io.opentelemetry.auto.tooling.Instrumenter;
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
                .setAttribute(Tags.SPAN_KIND, Tags.SPAN_KIND_CLIENT)
                .setAttribute(MoreTags.SPAN_TYPE, SpanTypes.HTTP_CLIENT)
                .setAttribute(Tags.COMPONENT, COMPONENT);

        try (final AgentScope scope = activateSpan(span, false)) {
          span.setAttribute(Tags.HTTP_URL, url.toString());
          span.setAttribute(Tags.PEER_PORT, url.getPort() == -1 ? 80 : url.getPort());
          span.setAttribute(Tags.PEER_HOSTNAME, url.getHost());
          if (Config.get().isHttpClientSplitByDomain()) {
            span.setAttribute(MoreTags.SERVICE_NAME, url.getHost());
          }

          span.setError(true);
          span.addThrowable(throwable);
          span.finish();
        }
      }
    }
  }
}
