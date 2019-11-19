package datadog.opentracing.propagation;

import static datadog.opentracing.propagation.HttpCodec.validateUInt64BitsID;

import datadog.opentracing.DDSpanContext;
import io.opentracing.SpanContext;
import io.opentracing.propagation.TextMapExtract;
import io.opentracing.propagation.TextMapInject;
import java.math.BigInteger;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/** A codec designed for HTTP transport via headers using Datadog headers */
@Slf4j
class DatadogHttpCodec {

  private static final String OT_BAGGAGE_PREFIX = "ot-baggage-";
  private static final String TRACE_ID_KEY = "x-datadog-trace-id";
  private static final String SPAN_ID_KEY = "x-datadog-parent-id";
  private static final String ORIGIN_KEY = "x-datadog-origin";

  private DatadogHttpCodec() {
    // This class should not be created. This also makes code coverage checks happy.
  }

  public static class Injector implements HttpCodec.Injector {

    @Override
    public void inject(final DDSpanContext context, final TextMapInject carrier) {
      carrier.put(TRACE_ID_KEY, context.getTraceId().toString());
      carrier.put(SPAN_ID_KEY, context.getSpanId().toString());
      final String origin = context.getOrigin();
      if (origin != null) {
        carrier.put(ORIGIN_KEY, origin);
      }

      for (final Map.Entry<String, String> entry : context.baggageItems()) {
        carrier.put(OT_BAGGAGE_PREFIX + entry.getKey(), HttpCodec.encode(entry.getValue()));
      }
      log.debug("{} - Datadog parent context injected", context.getTraceId());
    }
  }

  public static class Extractor implements HttpCodec.Extractor {
    private final Map<String, String> taggedHeaders;

    public Extractor(final Map<String, String> taggedHeaders) {
      this.taggedHeaders = new HashMap<>();
      for (final Map.Entry<String, String> mapping : taggedHeaders.entrySet()) {
        this.taggedHeaders.put(mapping.getKey().trim().toLowerCase(), mapping.getValue());
      }
    }

    @Override
    public SpanContext extract(final TextMapExtract carrier) {
      try {
        Map<String, String> baggage = Collections.emptyMap();
        Map<String, String> tags = Collections.emptyMap();
        BigInteger traceId = BigInteger.ZERO;
        BigInteger spanId = BigInteger.ZERO;
        String origin = null;

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
          } else if (ORIGIN_KEY.equalsIgnoreCase(key)) {
            origin = value;
          } else if (key.startsWith(OT_BAGGAGE_PREFIX)) {
            if (baggage.isEmpty()) {
              baggage = new HashMap<>();
            }
            baggage.put(key.replace(OT_BAGGAGE_PREFIX, ""), HttpCodec.decode(value));
          }

          if (taggedHeaders.containsKey(key)) {
            if (tags.isEmpty()) {
              tags = new HashMap<>();
            }
            tags.put(taggedHeaders.get(key), HttpCodec.decode(value));
          }
        }

        if (!BigInteger.ZERO.equals(traceId)) {
          final ExtractedContext context =
              new ExtractedContext(traceId, spanId, origin, baggage, tags);

          log.debug("{} - Parent context extracted", context.getTraceId());
          return context;
        } else if (origin != null || !tags.isEmpty()) {
          log.debug("Tags context extracted");
          return new TagContext(origin, tags);
        }
      } catch (final RuntimeException e) {
        log.debug("Exception when extracting context", e);
      }

      return null;
    }
  }
}
