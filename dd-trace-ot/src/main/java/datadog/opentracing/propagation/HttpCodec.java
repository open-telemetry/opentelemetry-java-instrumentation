package datadog.opentracing.propagation;

import datadog.opentracing.DDSpanContext;
import datadog.opentracing.DDTracer;
import java.math.BigInteger;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HttpCodec {
  public interface Injector {

    void inject(final DDSpanContext context, final TextMapInject carrier);
  }

  public interface Extractor {

    ExtractedContext extract(final TextMapExtract carrier);
  }

  public static Injector createInjector() {
    return new DatadogHttpCodec.Injector();
  }

  public static Extractor createExtractor() {
    return new DatadogHttpCodec.Extractor();
  }

  /**
   * Helper method to validate an ID String to verify within range
   *
   * @param value the String that contains the ID
   * @param radix radix to use to parse the ID
   * @return the parsed ID
   * @throws IllegalArgumentException if value cannot be converted to integer or doesn't conform to
   *     required boundaries
   */
  static BigInteger validateUInt64BitsID(final String value, final int radix)
      throws IllegalArgumentException {
    final BigInteger parsedValue = new BigInteger(value, radix);
    if (parsedValue.compareTo(DDTracer.TRACE_ID_MIN) < 0
        || parsedValue.compareTo(DDTracer.TRACE_ID_MAX) > 0) {
      throw new IllegalArgumentException(
          "ID out of range, must be between 0 and 2^64-1, got: " + value);
    }

    return parsedValue;
  }
}
