package datadog.trace.instrumentation.spymemcached;

import static io.opentracing.log.Fields.ERROR_OBJECT;

import datadog.trace.api.DDSpanTypes;
import datadog.trace.api.DDTags;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import java.util.Collections;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class CompletionListener<T> {

  // Note: it looks like this value is being ignored and DBTypeDecorator overwrites it.
  static final String OPERATION_NAME = "memcached.query";

  static final String SERVICE_NAME = "memcached";
  static final String COMPONENT_NAME = "java-spymemcached";
  static final String DB_TYPE = "memcached";
  static final String DB_COMMAND_CANCELLED = "db.command.cancelled";
  static final String MEMCACHED_RESULT = "memcaced.result";
  static final String HIT = "hit";
  static final String MISS = "miss";

  private final Tracer tracer;
  private final Scope scope;

  public CompletionListener(final Tracer tracer, final String methodName, final boolean async) {
    this.tracer = tracer;
    scope = buildSpan(getOperationName(methodName), async);
  }

  private Scope buildSpan(final String operation, final boolean async) {
    final Tracer.SpanBuilder spanBuilder =
        tracer
            .buildSpan(OPERATION_NAME)
            .withTag(DDTags.SERVICE_NAME, SERVICE_NAME)
            .withTag(DDTags.RESOURCE_NAME, operation)
            .withTag(DDTags.SPAN_TYPE, DDSpanTypes.MEMCACHED)
            .withTag(Tags.COMPONENT.getKey(), COMPONENT_NAME)
            .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
            .withTag(Tags.DB_TYPE.getKey(), DB_TYPE);

    final Scope scope = spanBuilder.startActive(false);
    if (async) {
      scope.close();
    }
    return scope;
  }

  protected void closeAsyncSpan(final T future) {
    final Span span = scope.span();
    try {
      processResult(span, future);
    } catch (final CancellationException e) {
      span.setTag(DB_COMMAND_CANCELLED, true);
    } catch (final ExecutionException e) {
      if (e.getCause() instanceof CancellationException) {
        // Looks like underlying OperationFuture wraps CancellationException into ExecutionException
        span.setTag(DB_COMMAND_CANCELLED, true);
      } else {
        Tags.ERROR.set(span, Boolean.TRUE);
        span.log(Collections.singletonMap(ERROR_OBJECT, e.getCause()));
      }
    } catch (final InterruptedException e) {
      // Avoid swallowing InterruptedException
      Tags.ERROR.set(span, Boolean.TRUE);
      span.log(Collections.singletonMap(ERROR_OBJECT, e));
      Thread.currentThread().interrupt();
    } catch (final Exception e) {
      // This should never happen, just in case to make sure we cover all unexpected exceptions
      Tags.ERROR.set(span, Boolean.TRUE);
      span.log(Collections.singletonMap(ERROR_OBJECT, e));
    } finally {
      span.finish();
    }
  }

  protected void closeSyncSpan(final Throwable thrown) {
    final Span span = scope.span();

    if (thrown != null) {
      Tags.ERROR.set(span, Boolean.TRUE);
      span.log(Collections.singletonMap(ERROR_OBJECT, thrown));
    }

    span.finish();
    scope.close();
  }

  protected abstract void processResult(Span span, T future)
      throws ExecutionException, InterruptedException;

  protected void setResultTag(final Span span, final boolean hit) {
    span.setTag(MEMCACHED_RESULT, hit ? HIT : MISS);
  }

  private static String getOperationName(final String methodName) {
    final char[] chars =
        methodName
            .replaceFirst("^async", "")
            // 'CAS' name is special, we have to lowercase whole name
            .replaceFirst("^CAS", "cas")
            .toCharArray();

    // Lowercase first letter
    chars[0] = Character.toLowerCase(chars[0]);
    return new String(chars);
  }
}
