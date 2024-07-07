package io.opentelemetry.javaagent.instrumentation.twitterutilstats.v23_11;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.annotations.VisibleForTesting;
import com.twitter.finagle.stats.Bytes$;
import com.twitter.finagle.stats.Counter;
import com.twitter.finagle.stats.CustomUnit;
import com.twitter.finagle.stats.Gauge;
import com.twitter.finagle.stats.Kilobytes$;
import com.twitter.finagle.stats.Megabytes$;
import com.twitter.finagle.stats.Metadata;
import com.twitter.finagle.stats.MetricBuilder;
import com.twitter.finagle.stats.MetricUnit;
import com.twitter.finagle.stats.Microseconds$;
import com.twitter.finagle.stats.Milliseconds$;
import com.twitter.finagle.stats.Percentage$;
import com.twitter.finagle.stats.Requests$;
import com.twitter.finagle.stats.Seconds$;
import com.twitter.finagle.stats.Stat;
import com.twitter.finagle.stats.StatsReceiver;
import com.twitter.finagle.stats.Unspecified$;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleGaugeBuilder;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongCounterBuilder;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableDoubleGauge;
import io.opentelemetry.javaagent.bootstrap.internal.AgentInstrumentationConfig;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import scala.jdk.CollectionConverters;

public class OtelStatsReceiver implements StatsReceiver {

  private static final Logger logger = Logger.getLogger(OtelStatsReceiver.class.getName());

  private static final String DEFAULT_DELIM = ".";

  private static final Pattern NAME_REGEX = Pattern.compile("^[A-Za-z][_.-/A-Za-z0-9]{0,254}$");

  private static final Map<String, DoubleHistogram> HISTO_MAP = new ConcurrentHashMap<>();
  private static final Map<String, ObservableDoubleGauge> GAUGE_MAP = new ConcurrentHashMap<>();
  private static final Map<String, LongCounter> COUNTER_MAP = new ConcurrentHashMap<>();

  private static final Meter meter = GlobalOpenTelemetry.get().getMeter("twitter-util-stats");

  private static final AttributeKey<List<String>> HIERARCHICAL_NAME_LABEL_KEY =
      AttributeKey.stringArrayKey("twitter-util-stats/hierarchical_name");

  private static final String delimiter;

  static {
    String delim =
        AgentInstrumentationConfig.get()
            .getString(
                "otel.instrumentation.twitter-util-stats.metrics.name.delimiter", DEFAULT_DELIM);

    if (delim.isEmpty()) {
      logger.warning("Delimiter must be non-empty");
      delim = DEFAULT_DELIM;
    } else if (!delim.equals(".") && !delim.equals("/") && !delim.equals("-")) {
      logger.warning("Unsupported delimiter detected: " + delim);
      delim = DEFAULT_DELIM;
    }

    delimiter = delim;
  }

  public OtelStatsReceiver() {}

  @Override
  public Object repr() {
    return this;
  }

  @VisibleForTesting
  static String nameConversion(MetricBuilder schema) {
    return schema.identity().dimensionalName().mkString(delimiter);
  }

  /*
  Identical semantics to the finagle-stats PrometheusExporter.
   */
  @VisibleForTesting
  static boolean shouldEmit(MetricBuilder schema) {
    return schema
            .identity()
            .identityType()
            .bias(com.twitter.finagle.stats.MetricBuilder$IdentityType$HierarchicalOnly$.MODULE$)
        == com.twitter.finagle.stats.MetricBuilder$IdentityType$Full$.MODULE$;
  }

  /*
  Guard against avoidable, incorrect metric names.
  Finagle produces and emits a number of metrics which contain invalid chars and entirely free-form,
  making them hard to adapt to the otel spec, whitespace, in particular. This, along with names
  containing other identifying attributes which are unpredictable except in specific known cases
  but which are indistinguishable from other potential metrics. IOW, a metric bearing some pattern
  of identifying attributes in its name is indistinguishable from others created for a different
  purpose and in a different context and is therefore not reasonably translated into otel metrics.

  Suggestion: write another instrumentation that adapts those specific cases.
   */
  @VisibleForTesting
  static boolean canEmit(MetricBuilder schema) {
    return CollectionConverters.SeqHasAsJava(schema.identity().dimensionalName()).asJava().stream()
        .allMatch(NAME_REGEX.asPredicate());
  }

  @VisibleForTesting
  static Attributes attributesFromLabels(MetricBuilder builder) {
    return builder
        .identity()
        .labels()
        .foldLeft(
            // put the hierarchical name as a label bc hierarchical names are not cleanly mapped
            // to dimensional names via twitter stats or the final usage in finagle in a way otel
            // can concisely reason about; by adding the label, downstream systems can aggregate
            // on their own -- or not -- achieving a finer granularity without compromising
            // the general patterns applied herein, and without the complexities applied in
            // the finagle PrometheusExporter (implicitly, how the MetricsView & registries work,
            // etc.), for example
            Attributes.builder()
                .put(
                    HIERARCHICAL_NAME_LABEL_KEY,
                    scala.jdk.CollectionConverters.SeqHasAsJava(
                            builder.identity().hierarchicalName())
                        .asJava()),
            (v1, v2) -> v1.put(v2._1(), v2._2()))
        .build();
  }

  /*
  Always use "byte" for size units to avoid precision loss.
  Why: the aggregating services treat unit scales uniformly, so this should present little issue.
   */
  // unchecked casting to efficiently handle the generic number conversion for long vs float
  @SuppressWarnings("unchecked")
  private static <T extends Number> UnitConversion<T> unitConverter(
      MetricUnit unit, MetricBuilder.MetricType metricType) {
    if (unit instanceof CustomUnit) {
      return UnitConversion.create(((CustomUnit) unit).name().toLowerCase(Locale.getDefault()));
    } else if (unit instanceof Unspecified$) {
      return UnitConversion.create();
    } else if (unit instanceof Bytes$) {
      return UnitConversion.create("byte");
    } else if (unit instanceof Kilobytes$) {
      // base-10 to base-2 translation
      if (metricType == MetricBuilder.CounterType$.MODULE$) {
        return (UnitConversion<T>) UnitConversion.create("byte", (Long x) -> (x * 1000));
      } else {
        return (UnitConversion<T>) UnitConversion.create("byte", (Float x) -> (x * 1000));
      }
    } else if (unit instanceof Megabytes$) {
      // base-10 to base-2 translation
      if (metricType == MetricBuilder.CounterType$.MODULE$) {
        return (UnitConversion<T>) UnitConversion.create("byte", (Long x) -> (x * 1000 * 1000));
      } else {
        return (UnitConversion<T>) UnitConversion.create("byte", (Float x) -> (x * 1000 * 1000));
      }
    } else if (unit instanceof Seconds$) {
      return UnitConversion.create("second");
    } else if (unit instanceof Milliseconds$) {
      return UnitConversion.create("millisecond");
    } else if (unit instanceof Microseconds$) {
      return UnitConversion.create("microsecond");
    } else if (unit instanceof Requests$) {
      return UnitConversion.create("request");
    } else if (unit instanceof Percentage$) {
      return UnitConversion.create("percent");
    } else {
      throw new IllegalArgumentException("unsupported metric unit: " + unit.toString());
    }
  }

  @Override
  public Stat stat(MetricBuilder schema) {
    Attributes attributes = attributesFromLabels(schema);
    UnitConversion<Float> conversion = unitConverter(schema.units(), schema.metricType());

    if (!canEmit(schema) && !shouldEmit(schema)) {
      logger.fine("skipping stat: " + nameConversion(schema));
      return OtelStat.createNull(schema, attributes, conversion);
    }

    logger.fine("instrumenting stat: " + nameConversion(schema));
    return OtelStat.create(
        schema,
        attributes,
        conversion,
        HISTO_MAP.computeIfAbsent(
            nameConversion(schema),
            name -> {
              DoubleHistogramBuilder builder =
                  meter.histogramBuilder(name).setDescription(schema.description());
              if (conversion.getUnits().isPresent()) {
                builder = builder.setUnit(conversion.getUnits().get());
              }
              if (schema.percentiles().nonEmpty()) {
                builder =
                    builder.setExplicitBucketBoundariesAdvice(
                        CollectionConverters.SeqHasAsJava(schema.percentiles()).asJava().stream()
                            .map(scala.Double::unbox)
                            .collect(Collectors.toList()));
              }
              return builder.build();
            }));
  }

  @Override
  public Gauge addGauge(MetricBuilder metricBuilder, scala.Function0<Object> f) {
    if (!canEmit(metricBuilder) && !shouldEmit(metricBuilder)) {
      logger.fine("skipping gauge: " + nameConversion(metricBuilder));
      return OtelGauge.createNull(metricBuilder);
    }

    logger.fine("instrumenting gauge: " + nameConversion(metricBuilder));
    return OtelGauge.create(
        metricBuilder,
        GAUGE_MAP.computeIfAbsent(
            nameConversion(metricBuilder),
            name -> {
              Attributes attributes = attributesFromLabels(metricBuilder);

              UnitConversion<Float> conversion =
                  unitConverter(metricBuilder.units(), metricBuilder.metricType());

              DoubleGaugeBuilder builder =
                  meter.gaugeBuilder(name).setDescription(metricBuilder.description());
              if (conversion.getUnits().isPresent()) {
                builder = builder.setUnit(conversion.getUnits().get());
              }
              return builder.buildWithCallback(
                  odm ->
                      odm.record(conversion.getConverter().apply((float) f.apply()), attributes));
            }));
  }

  @Override
  public Counter counter(MetricBuilder schema) {
    Attributes attributes = attributesFromLabels(schema);
    UnitConversion<Long> conversion = unitConverter(schema.units(), schema.metricType());

    if (!canEmit(schema) && !shouldEmit(schema)) {
      logger.fine("skipping counter: " + nameConversion(schema));
      return OtelCounter.createNull(schema, attributes, conversion);
    }

    logger.fine("instrumenting counter: " + nameConversion(schema));
    return OtelCounter.create(
        schema,
        attributes,
        conversion,
        COUNTER_MAP.computeIfAbsent(
            nameConversion(schema),
            name -> {
              logger.fine("creating counter: " + nameConversion(schema));
              LongCounterBuilder builder =
                  meter.counterBuilder(name).setDescription(schema.description());
              if (conversion.getUnits().isPresent()) {
                builder = builder.setUnit(conversion.getUnits().get());
              }
              return builder.build();
            }));
  }

  @AutoValue
  abstract static class UnitConversion<T extends Number> {
    abstract Optional<String> getUnits();

    abstract Function<T, T> getConverter();

    static <T extends Number> UnitConversion<T> create(String units, Function<T, T> converter) {
      return new AutoValue_OtelStatsReceiver_UnitConversion<>(Optional.of(units), converter);
    }

    static <T extends Number> UnitConversion<T> create(String units) {
      return create(units, Function.identity());
    }

    static <T extends Number> UnitConversion<T> create() {
      return new AutoValue_OtelStatsReceiver_UnitConversion<>(
          Optional.empty(), Function.identity());
    }
  }

  @AutoValue
  abstract static class OtelGauge implements Gauge {
    abstract MetricBuilder getSchema();

    @Nullable
    abstract ObservableDoubleGauge getGauge();

    static OtelGauge createNull(MetricBuilder schema) {
      return new AutoValue_OtelStatsReceiver_OtelGauge(schema, null);
    }

    static OtelGauge create(MetricBuilder schema, ObservableDoubleGauge gauge) {
      return new AutoValue_OtelStatsReceiver_OtelGauge(schema, gauge);
    }

    @Memoized
    boolean isEmitted() {
      return getGauge() != null;
    }

    @Override
    public void remove() {
      if (getGauge() == null) {
        return;
      }
      getGauge().close();
    }

    @Override
    public Metadata metadata() {
      return getSchema();
    }
  }

  @AutoValue
  abstract static class OtelCounter implements Counter {
    abstract MetricBuilder getSchema();

    abstract Attributes getAttributes();

    abstract UnitConversion<Long> getConversion();

    @Nullable
    abstract LongCounter getCounter();

    static OtelCounter createNull(
        MetricBuilder schema, Attributes attributes, UnitConversion<Long> conversion) {
      return new AutoValue_OtelStatsReceiver_OtelCounter(schema, attributes, conversion, null);
    }

    static OtelCounter create(
        MetricBuilder schema,
        Attributes attributes,
        UnitConversion<Long> conversion,
        LongCounter counter) {
      return new AutoValue_OtelStatsReceiver_OtelCounter(schema, attributes, conversion, counter);
    }

    @Memoized
    boolean isEmitted() {
      return getCounter() != null;
    }

    @Override
    public void incr(long delta) {
      if (getCounter() == null) {
        return;
      }
      getCounter().add(getConversion().getConverter().apply(delta), getAttributes());
    }

    @Override
    public Metadata metadata() {
      return getSchema();
    }
  }

  @AutoValue
  abstract static class OtelStat implements Stat {
    abstract MetricBuilder getSchema();

    abstract Attributes getAttributes();

    abstract UnitConversion<Float> getConversion();

    @Nullable
    abstract DoubleHistogram getHistogram();

    static OtelStat createNull(
        MetricBuilder schema, Attributes attributes, UnitConversion<Float> conversion) {
      return new AutoValue_OtelStatsReceiver_OtelStat(schema, attributes, conversion, null);
    }

    static OtelStat create(
        MetricBuilder schema,
        Attributes attributes,
        UnitConversion<Float> conversion,
        DoubleHistogram histogram) {
      return new AutoValue_OtelStatsReceiver_OtelStat(schema, attributes, conversion, histogram);
    }

    @Memoized
    boolean isEmitted() {
      return getHistogram() != null;
    }

    @Override
    public void add(float value) {
      if (getHistogram() == null) {
        return;
      }
      getHistogram().record(getConversion().getConverter().apply(value), getAttributes());
    }

    @Override
    public Metadata metadata() {
      return getSchema();
    }
  }
}
