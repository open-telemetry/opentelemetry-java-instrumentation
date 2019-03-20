package datadog.opentracing.propagation;

import static datadog.opentracing.propagation.HttpCodec.ZERO;
import static datadog.opentracing.propagation.HttpCodec.validateUInt64BitsID;

import datadog.opentracing.DDSpanContext;
import datadog.trace.api.sampling.PrioritySampling;
import io.opentracing.SpanContext;
import io.opentracing.propagation.TextMap;
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
  private static final String SAMPLING_PRIORITY_KEY = "X-B3-Sampled";
  private static final String SAMPLING_PRIORITY_ACCEPT = String.valueOf(1);
  private static final String SAMPLING_PRIORITY_DROP = String.valueOf(0);
  private static final int HEX_RADIX = 16;

  private B3HttpCodec() {
    // This class should not be created. This also makes code coverage checks happy.
  }

  public static class Injector implements HttpCodec.Injector {

    @Override
    public void inject(final DDSpanContext context, final TextMap carrier) {
      try {
        // TODO: should we better store ids as BigInteger in context to avoid parsing it twice.
        final BigInteger traceId = new BigInteger(context.getTraceId());
        final BigInteger spanId = new BigInteger(context.getSpanId());

        carrier.put(TRACE_ID_KEY, traceId.toString(HEX_RADIX).toLowerCase());
        carrier.put(SPAN_ID_KEY, spanId.toString(HEX_RADIX).toLowerCase());

        if (context.lockSamplingPriority()) {
          carrier.put(
              SAMPLING_PRIORITY_KEY, convertSamplingPriority(context.getSamplingPriority()));
        }
        log.debug("{} - B3 parent context injected", context.getTraceId());
      } catch (final NumberFormatException e) {
        log.debug(
            "Cannot parse context id(s): {} {}", context.getTraceId(), context.getSpanId(), e);
      }
    }

    private String convertSamplingPriority(final int samplingPriority) {
      return samplingPriority > 0 ? SAMPLING_PRIORITY_ACCEPT : SAMPLING_PRIORITY_DROP;
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
    public SpanContext extract(final TextMap carrier) {
      try {
        Map<String, String> tags = Collections.emptyMap();
        String traceId = ZERO;
        String spanId = ZERO;
        int samplingPriority = PrioritySampling.UNSET;

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
              traceId = "0";
              continue;
            } else if (length > 16) {
              trimmedValue = value.substring(length - 16);
            } else {
              trimmedValue = value;
            }
            traceId = validateUInt64BitsID(trimmedValue, HEX_RADIX);
          } else if (SPAN_ID_KEY.equalsIgnoreCase(key)) {
            spanId = validateUInt64BitsID(value, HEX_RADIX);
          } else if (SAMPLING_PRIORITY_KEY.equalsIgnoreCase(key)) {
            samplingPriority = convertSamplingPriority(value);
          }

          if (taggedHeaders.containsKey(key)) {
            if (tags.isEmpty()) {
              tags = new HashMap<>();
            }
            tags.put(taggedHeaders.get(key), HttpCodec.decode(value));
          }
        }

        if (!ZERO.equals(traceId)) {
          final ExtractedContext context =
              new ExtractedContext(
                  traceId,
                  spanId,
                  samplingPriority,
                  null,
                  Collections.<String, String>emptyMap(),
                  tags);
          context.lockSamplingPriority();

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

    private int convertSamplingPriority(final String samplingPriority) {
      return Integer.parseInt(samplingPriority) == 1
          ? PrioritySampling.SAMPLER_KEEP
          : PrioritySampling.SAMPLER_DROP;
    }
  }
}
