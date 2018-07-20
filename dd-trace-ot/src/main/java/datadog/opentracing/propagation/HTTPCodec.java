package datadog.opentracing.propagation;

import datadog.opentracing.DDSpanContext;
import datadog.trace.api.sampling.PrioritySampling;
import io.opentracing.propagation.TextMap;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/** A codec designed for HTTP transport via headers */
@Slf4j
public class HTTPCodec implements Codec<TextMap> {

  private static final String OT_BAGGAGE_PREFIX = "ot-baggage-";
  private static final String TRACE_ID_KEY = "x-datadog-trace-id";
  private static final String SPAN_ID_KEY = "x-datadog-parent-id";
  private static final String SAMPLING_PRIORITY_KEY = "x-datadog-sampling-priority";
  private static final byte[] BYTE_ARR_UNIT64_MAX = {
    (byte) 0xff,
    (byte) 0xff,
    (byte) 0xff,
    (byte) 0xff,
    (byte) 0xff,
    (byte) 0xff,
    (byte) 0xff,
    (byte) 0xff
  };
  private static final BigInteger BIG_INTEGER_UINT64_MAX =
      (new BigInteger(BYTE_ARR_UNIT64_MAX)).add(BigInteger.ONE.shiftLeft(64));

  private final Map<String, String> taggedHeaders;

  public HTTPCodec(final Map<String, String> taggedHeaders) {
    this.taggedHeaders = new HashMap<>();
    for (final Map.Entry<String, String> mapping : taggedHeaders.entrySet()) {
      this.taggedHeaders.put(mapping.getKey().trim().toLowerCase(), mapping.getValue());
    }
  }

  @Override
  public void inject(final DDSpanContext context, final TextMap carrier) {
    carrier.put(TRACE_ID_KEY, String.valueOf(context.getTraceId()));
    carrier.put(SPAN_ID_KEY, String.valueOf(context.getSpanId()));
    if (context.lockSamplingPriority()) {
      carrier.put(SAMPLING_PRIORITY_KEY, String.valueOf(context.getSamplingPriority()));
    }

    for (final Map.Entry<String, String> entry : context.baggageItems()) {
      carrier.put(OT_BAGGAGE_PREFIX + entry.getKey(), encode(entry.getValue()));
    }
    log.debug("{} - Parent context injected", context.getTraceId());
  }

  @Override
  public ExtractedContext extract(final TextMap carrier) {

    Map<String, String> baggage = Collections.emptyMap();
    Map<String, String> tags = Collections.emptyMap();
    String traceId = "0";
    String spanId = "0";
    int samplingPriority = PrioritySampling.UNSET;

    for (final Map.Entry<String, String> entry : carrier) {
      final String key = entry.getKey().toLowerCase();
      final String val = entry.getValue();

      if (TRACE_ID_KEY.equalsIgnoreCase(key)) {
        traceId = validateUInt64BitsID(val);
      } else if (SPAN_ID_KEY.equalsIgnoreCase(key)) {
        spanId = validateUInt64BitsID(val);
      } else if (key.startsWith(OT_BAGGAGE_PREFIX)) {
        if (baggage.isEmpty()) {
          baggage = new HashMap<>();
        }
        baggage.put(key.replace(OT_BAGGAGE_PREFIX, ""), decode(val));
      } else if (SAMPLING_PRIORITY_KEY.equalsIgnoreCase(key)) {
        samplingPriority = Integer.parseInt(val);
      }

      if (taggedHeaders.containsKey(key)) {
        if (tags.isEmpty()) {
          tags = new HashMap<>();
        }
        tags.put(taggedHeaders.get(key), decode(val));
      }
    }
    ExtractedContext context = null;
    if (!"0".equals(traceId)) {
      context = new ExtractedContext(traceId, spanId, samplingPriority, baggage, tags);
      context.lockSamplingPriority();

      log.debug("{} - Parent context extracted", context.getTraceId());
    }

    return context;
  }

  private String encode(final String value) {
    String encoded = value;
    try {
      encoded = URLEncoder.encode(value, "UTF-8");
    } catch (final UnsupportedEncodingException e) {
      log.info("Failed to encode value - {}", value);
    }
    return encoded;
  }

  private String decode(final String value) {
    String decoded = value;
    try {
      decoded = URLDecoder.decode(value, "UTF-8");
    } catch (final UnsupportedEncodingException e) {
      log.info("Failed to decode value - {}", value);
    }
    return decoded;
  }

  /**
   * Helper method to validate an ID String to verify that it is an unsigned 64 bits number and is
   * within range.
   *
   * @param val the String that contains the ID
   * @return the ID in String format if it passes validations
   * @throws IllegalArgumentException if val is not a number or if the number is out of range
   */
  private String validateUInt64BitsID(String val) throws IllegalArgumentException {
    try {
      BigInteger validate = new BigInteger(val);
      if (validate.compareTo(BigInteger.ZERO) == -1
          || validate.compareTo(BIG_INTEGER_UINT64_MAX) == 1) {
        throw new IllegalArgumentException(
            "ID out of range, must be between 0 and 2^64-1, got: " + val);
      }
      return val;
    } catch (NumberFormatException nfe) {
      throw new IllegalArgumentException(
          "Expecting a number for trace ID or span ID, but got: " + val);
    }
  }
}
