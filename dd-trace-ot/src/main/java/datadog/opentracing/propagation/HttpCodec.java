package datadog.opentracing.propagation;

import datadog.opentracing.DDSpanContext;
import datadog.trace.api.Config;
import io.opentracing.SpanContext;
import io.opentracing.propagation.TextMap;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HttpCodec {

  // uint 64 bits max value, 2^64 - 1
  static final BigInteger UINT64_MAX = new BigInteger("2").pow(64).subtract(BigInteger.ONE);
  static final String ZERO = "0";

  public interface Injector {

    void inject(final DDSpanContext context, final TextMap carrier);
  }

  public interface Extractor {

    SpanContext extract(final TextMap carrier);
  }

  public static Injector createInjector(final Config config) {
    final List<Injector> injectors = new ArrayList<>();
    for (final Config.PropagationStyle style : config.getPropagationStylesToInject()) {
      if (style == Config.PropagationStyle.DATADOG) {
        injectors.add(new DatadogHttpCodec.Injector());
        continue;
      }
      if (style == Config.PropagationStyle.B3) {
        injectors.add(new B3HttpCodec.Injector());
        continue;
      }
      log.debug("No implementation found to inject propagation style: {}", style);
    }
    return new CompoundInjector(injectors);
  }

  public static Extractor createExtractor(
      final Config config, final Map<String, String> taggedHeaders) {
    final List<Extractor> extractors = new ArrayList<>();
    for (final Config.PropagationStyle style : config.getPropagationStylesToExtract()) {
      if (style == Config.PropagationStyle.DATADOG) {
        extractors.add(new DatadogHttpCodec.Extractor(taggedHeaders));
        continue;
      }
      if (style == Config.PropagationStyle.B3) {
        extractors.add(new B3HttpCodec.Extractor());
        continue;
      }
      log.debug("No implementation found to extract propagation style: {}", style);
    }
    return new CompoundExtractor(extractors);
  }

  public static class CompoundInjector implements Injector {

    private final List<Injector> injectors;

    public CompoundInjector(final List<Injector> injectors) {
      this.injectors = injectors;
    }

    @Override
    public void inject(final DDSpanContext context, final TextMap carrier) {
      for (final Injector injector : injectors) {
        injector.inject(context, carrier);
      }
    }
  }

  public static class CompoundExtractor implements Extractor {

    private final List<Extractor> extractors;

    public CompoundExtractor(final List<Extractor> extractors) {
      this.extractors = extractors;
    }

    @Override
    public SpanContext extract(final TextMap carrier) {
      for (final Extractor extractor : extractors) {
        final SpanContext context = extractor.extract(carrier);
        if (context != null) {
          return context;
        }
      }
      return null;
    }
  }

  /**
   * Helper method to validate an ID String to verify that it is an unsigned 64 bits number and is
   * within range.
   *
   * @param value the String that contains the ID
   * @param radix radix to use to parse the ID
   * @return the ID in String format if it passes validations, "0" otherwise
   * @throws IllegalArgumentException if value cannot be converted to integer or doesn't conform to
   *     required boundaries
   */
  static String validateUInt64BitsID(final String value, final int radix)
      throws IllegalArgumentException {
    final BigInteger parsedValue = new BigInteger(value, radix);
    if (parsedValue.compareTo(BigInteger.ZERO) == -1 || parsedValue.compareTo(UINT64_MAX) == 1) {
      throw new IllegalArgumentException(
          "ID out of range, must be between 0 and 2^64-1, got: " + value);
    }
    // We use decimals
    return parsedValue.toString();
  }
}
