package datadog.trace.instrumentation.springwebflux;

import static io.opentracing.log.Fields.ERROR_OBJECT;

import datadog.trace.api.DDSpanTypes;
import datadog.trace.api.DDTags;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.util.Collections;
import java.util.function.BiConsumer;

public class DispatcherHandlerMonoBiConsumer<U> implements BiConsumer<U, Throwable> {

  private final Scope scope;
  public static final ThreadLocal<String> tlsPathUrlTag = new ThreadLocal<>();

  public DispatcherHandlerMonoBiConsumer(final Scope scope) {
    this.scope = scope;
  }

  @Override
  public void accept(final U object, final Throwable throwable) {
    final Span spanToChange = scope.span();
    if (throwable != null) {
      spanToChange.log(Collections.singletonMap(ERROR_OBJECT, throwable));
      Tags.ERROR.set(spanToChange, true);
    }

    scope.close();

    final Span parentSpan = GlobalTracer.get().activeSpan();
    final String pathUrl = tlsPathUrlTag.get();

    if (pathUrl != null && parentSpan != null) {
      parentSpan.setTag(DDTags.RESOURCE_NAME, pathUrl);
      parentSpan.setTag(DDTags.SPAN_TYPE, DDSpanTypes.WEB_SERVLET);
      tlsPathUrlTag.remove();
    }
  }

  public static void setTLPathUrl(final String pathUrl) {
    tlsPathUrlTag.set(pathUrl);
  }
}
