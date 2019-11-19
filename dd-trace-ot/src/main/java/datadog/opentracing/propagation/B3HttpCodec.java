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

/**
 * A codec designed for HTTP transport via headers using B3 headers
 *
 * <p>TODO: there is fair amount of code duplication between DatadogHttpCodec and this class,
 * especially in part where TagContext is handled. We may want to refactor that and avoid special
 * handling of TagContext in other places (i.e. CompoundExtractor).
 */
@Slf4j
class B3HttpCodec {

  private static final String TRACE_ID_KEY = "X-B3-TraceId";
  private static final String SPAN_ID_KEY = "X-B3-SpanId";
  private static final int HEX_RADIX = 16;

  private B3HttpCodec() {
    // This class should not be created. This also makes code coverage checks happy.
  }

  public static class Injector implements HttpCodec.Injector {

    @Override
    public void inject(final DDSpanContext context, final TextMapInject carrier) {
      try {
        carrier.put(TRACE_ID_KEY, context.getTraceId().toString(HEX_RADIX).toLowerCase());
        carrier.put(SPAN_ID_KEY, context.getSpanId().toString(HEX_RADIX).toLowerCase());
        log.debug("{} - B3 parent context injected", context.getTraceId());
      } catch (final NumberFormatException e) {
        log.debug(
            "Cannot parse context id(s): {} {}", context.getTraceId(), context.getSpanId(), e);
      }
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
        Map<String, String> tags = Collections.emptyMap();
        BigInteger traceId = BigInteger.ZERO;
        BigInteger spanId = BigInteger.ZERO;

        for (final Map.Entry<String, String> entry : carrier) {
          final String key = entry.getKey().toLowerCase();
          final String value = entry.getValue();

          if (value == null) {
            continue;
          }

          if (TRACE_ID_KEY.equalsIgnoreCase(key)) {
            final String trimmedValue;
            final int length = value.length();
            if (length > 32) {
              log.debug("Header {} exceeded max length of 32: {}", TRACE_ID_KEY, value);
              traceId = BigInteger.ZERO;
              continue;
            } else if (length > 16) {
              trimmedValue = value.substring(length - 16);
            } else {
              trimmedValue = value;
            }
            traceId = validateUInt64BitsID(trimmedValue, HEX_RADIX);
          } else if (SPAN_ID_KEY.equalsIgnoreCase(key)) {
            spanId = validateUInt64BitsID(value, HEX_RADIX);
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
              new ExtractedContext(
                  traceId, spanId, null, Collections.<String, String>emptyMap(), tags);

          log.debug("{} - Parent context extracted", context.getTraceId());
          return context;
        } else if (!tags.isEmpty()) {
          log.debug("Tags context extracted");
          return new TagContext(null, tags);
        }
      } catch (final RuntimeException e) {
        log.debug("Exception when extracting context", e);
      }

      return null;
    }
  }
}
