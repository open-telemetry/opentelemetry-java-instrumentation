package datadog.trace.instrumentation.http_url_connection;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;
import io.opentracing.util.GlobalTracer;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatcher;
import sun.net.www.MessageHeader;
import sun.net.www.http.HttpClient;

@AutoService(Instrumenter.class)
public class SunHttpClientInstrumentation extends Instrumenter.Default {

  public SunHttpClientInstrumentation() {
    super("httpurlconnection");
  }

  @Override
  protected boolean defaultEnabled() {
    return false;
  }

  @Override
  public ElementMatcher typeMatcher() {
    return named("sun.net.www.http.HttpClient");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      SunHttpClientInstrumentation.class.getName() + "$MessageHeadersInjectAdapter"
    };
  }

  @Override
  public Map<ElementMatcher, String> transformers() {
    return Collections.<ElementMatcher, String>singletonMap(
        isMethod()
            .and(isPublic())
            .and(named("writeRequests"))
            .and(takesArgument(0, named("sun.net.www.MessageHeader")))
            // exclude the delegating method:
            .and(takesArguments(1).or(takesArguments(2))),
        InjectAdvice.class.getName());
  }

  public static class InjectAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void inject(
        @Advice.Argument(0) final MessageHeader header, @Advice.This final HttpClient client) {
      final Tracer tracer = GlobalTracer.get();
      final Span span = tracer.activeSpan();

      if (span != null) {
        tracer.inject(
            span.context(), Format.Builtin.HTTP_HEADERS, new MessageHeadersInjectAdapter(header));
      }
    }
  }

  public static class MessageHeadersInjectAdapter implements TextMap {

    private final MessageHeader header;

    public MessageHeadersInjectAdapter(final MessageHeader header) {
      this.header = header;
    }

    @Override
    public void put(final String key, final String value) {
      header.setIfNotSet(key, value);
    }

    @Override
    public Iterator<Map.Entry<String, String>> iterator() {
      throw new UnsupportedOperationException(
          "This class should be used only with tracer#inject()");
    }
  }
}
