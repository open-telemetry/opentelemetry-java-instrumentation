package datadog.opentracing.propagation;

import static datadog.opentracing.propagation.HttpCodec.validateUInt64BitsID;

import datadog.opentracing.DDSpanContext;
import io.opentracing.propagation.TextMapExtract;
import io.opentracing.propagation.TextMapInject;
import java.math.BigInteger;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/** A codec designed for HTTP transport via headers using Datadog headers */
@Slf4j
class DatadogHttpCodec {

  private static final String TRACE_ID_KEY = "x-datadog-trace-id";
  private static final String SPAN_ID_KEY = "x-datadog-parent-id";

  private DatadogHttpCodec() {
    // This class should not be created. This also makes code coverage checks happy.
  }

  public static class Injector implements HttpCodec.Injector {

    @Override
    public void inject(final DDSpanContext context, final TextMapInject carrier) {
      carrier.put(TRACE_ID_KEY, context.getTraceId().toString());
      carrier.put(SPAN_ID_KEY, context.getSpanId().toString());

      log.debug("{} - Datadog parent context injected", context.getTraceId());
    }
  }

  public static class Extractor implements HttpCodec.Extractor {

    public Extractor() {}

    @Override
    public ExtractedContext extract(final TextMapExtract carrier) {
      try {
        BigInteger traceId = BigInteger.ZERO;
        BigInteger spanId = BigInteger.ZERO;

        for (final Map.Entry<String, String> entry : carrier) {
          final String key = entry.getKey().toLowerCase();
          final String value = entry.getValue();

          if (value == null) {
            continue;
          }

          if (TRACE_ID_KEY.equalsIgnoreCase(key)) {
            traceId = validateUInt64BitsID(value, 10);
          } else if (SPAN_ID_KEY.equalsIgnoreCase(key)) {
            spanId = validateUInt64BitsID(value, 10);
          }
        }

        if (!BigInteger.ZERO.equals(traceId)) {
          final ExtractedContext context = new ExtractedContext(traceId, spanId);

          log.debug("{} - Parent context extracted", context.getTraceId());
          return context;
        }
      } catch (final RuntimeException e) {
        log.debug("Exception when extracting context", e);
      }

      return null;
    }
  }
}
