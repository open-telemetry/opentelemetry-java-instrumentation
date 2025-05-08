package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import software.amazon.awssdk.core.metrics.CoreMetric;
import software.amazon.awssdk.http.HttpMetric;
import software.amazon.awssdk.metrics.MetricCollection;
import software.amazon.awssdk.metrics.MetricPublisher;
import software.amazon.awssdk.metrics.MetricRecord;

/**
 * A metrics reporter that reports AWS SDK metrics to OpenTelemetry.
 * The metric names, descriptions, and units are defined based on <a href="https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/metrics-list.html">AWS SDK Metrics List</a>.
 * <p>
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at any time.
 */
public class OpenTelemetryMetricPublisher implements MetricPublisher {
  private static final Logger logger = Logger.getLogger(OpenTelemetryMetricPublisher.class.getName());
  private static final String DEFAULT_METRIC_PREFIX = "aws.sdk";
  private final Attributes baseAttributes;

  private final Map<String, Map<Boolean, Map<Integer, Attributes>>> perRequestAttributesCache = new ConcurrentHashMap<>();
  private final Map<Attributes, Map<String, Attributes>> perAttemptAttributesCache = new ConcurrentHashMap<>();
  private final Map<Attributes, Map<Integer, Attributes>> perHttpAttributesCache = new ConcurrentHashMap<>();

  private final Executor executor;
  private final String metricPrefix;
  private final Map<String, MetricStrategy> perRequestMetrics;
  private final Map<String, MetricStrategy> perAttemptMetrics;
  private final Map<String, MetricStrategy> httpMetrics;

  public OpenTelemetryMetricPublisher(OpenTelemetry openTelemetry) {
    this(openTelemetry, DEFAULT_METRIC_PREFIX, ForkJoinPool.commonPool(), Attributes.empty());
  }

  public OpenTelemetryMetricPublisher(OpenTelemetry openTelemetry, String metricPrefix) {
    this(openTelemetry, metricPrefix, ForkJoinPool.commonPool(), Attributes.empty());
  }

  public OpenTelemetryMetricPublisher(OpenTelemetry openTelemetry, String metricPrefix,
      Executor executor) {
    this(openTelemetry, metricPrefix, executor, Attributes.empty());
  }

  public OpenTelemetryMetricPublisher(OpenTelemetry openTelemetry,
      String metricPrefix,
      Executor executor,
      Attributes baseAttributes) {
    Objects.requireNonNull(metricPrefix, "metricPrefix must not be null");
    Objects.requireNonNull(openTelemetry, "openTelemetry must not be null");
    Objects.requireNonNull(baseAttributes, "baseAttributes must not be null");

    if (executor == null) {
      logger.log(Level.WARNING,
          "An executor is not provided. The metrics will be published synchronously on the calling thread.");
    }
    Meter meter = openTelemetry.getMeter(metricPrefix);

    this.metricPrefix = metricPrefix + ".";
    this.executor = executor;
    this.baseAttributes = baseAttributes;
    this.perRequestMetrics = initStrategies(MetricSpec.Scope.REQUEST, meter);
    this.perAttemptMetrics = initStrategies(MetricSpec.Scope.ATTEMPT, meter);
    this.httpMetrics = initStrategies(MetricSpec.Scope.HTTP, meter);
  }

  @Override
  public void publish(MetricCollection metricCollection) {
    if (executor == null) {
      publishInternal(metricCollection);
      return;
    }

    try {
      executor.execute(() -> publishInternal(metricCollection));
    } catch (RejectedExecutionException ex) {
      logger.log(Level.WARNING,
          "Some AWS SDK client-side metrics have been dropped because an internal executor did not accept the task.",
          ex);
    }
  }

  @Override
  public void close() {
    // This publisher does not allocate any resources that need to be cleaned up.
  }

  private Map<String, MetricStrategy> initStrategies(MetricSpec.Scope scope, Meter meter) {
    return Arrays.stream(MetricSpec.values())
        .filter(s -> s.getScope() == scope)
        .collect(Collectors.toMap(
            MetricSpec::getSdkMetricName,
            metricSpec -> new MetricStrategyWithoutErrors(metricSpec.create(meter, metricPrefix))));
  }

  private void publishInternal(MetricCollection metricCollection) {
    try {
      // Start processing from the root per-request metrics
      processPerRequestMetrics(metricCollection);
    } catch (RuntimeException e) {
      logger.log(Level.SEVERE, "An error occurred while publishing metrics", e);
    }
  }

  private static void recordMetrics(Map<String, MetricRecord<?>> metricsMap,
      Attributes attributes,
      Map<String, MetricStrategy> metricStrategies) {
    for (Map.Entry<String, MetricStrategy> entry : metricStrategies.entrySet()) {
      MetricRecord<?> metricRecord = metricsMap.get(entry.getKey());
      if (metricRecord != null) {
        entry.getValue().record(metricRecord, attributes);
      }
    }
  }

  private void processPerRequestMetrics(MetricCollection requestMetrics) {
    Map<String, MetricRecord<?>> metricsMap = extractMetrics(requestMetrics);

    // Extract attributes for per-request metrics
    String operationName = getStringMetricValue(metricsMap, CoreMetric.OPERATION_NAME.name());
    boolean isSuccess = getBooleanMetricValue(metricsMap, CoreMetric.API_CALL_SUCCESSFUL.name());
    int retryCount = getIntMetricValue(metricsMap, CoreMetric.RETRY_COUNT.name());
    Attributes attributes = toPerRequestAttributes(operationName, isSuccess, retryCount);

    // Report per-request metrics
    recordMetrics(metricsMap, attributes, perRequestMetrics);

    // Process per-attempt metrics
    for (MetricCollection attemptMetrics : requestMetrics.children()) {
      processPerAttemptMetrics(attemptMetrics, attributes);
    }
  }

  private void processPerAttemptMetrics(MetricCollection attemptMetrics,
      Attributes parentAttributes) {
    Map<String, MetricRecord<?>> metricsMap = extractMetrics(attemptMetrics);

    // Extract ErrorType if present
    String errorType = getStringMetricValue(metricsMap, CoreMetric.ERROR_TYPE.name());

    // Build attributes including attempt number and error type
    Attributes attributes = toAttemptAttributes(parentAttributes, errorType);

    // Report per-attempt metrics
    recordMetrics(metricsMap, attributes, perAttemptMetrics);

    // Process HTTP metrics
    for (MetricCollection httpMetricsCollection : attemptMetrics.children()) {
      processHttpMetrics(httpMetricsCollection, attributes);
    }
  }

  private void processHttpMetrics(MetricCollection httpMetricsCollection,
      Attributes parentAttributes) {
    Map<String, MetricRecord<?>> metricsMap = extractMetrics(httpMetricsCollection);

    // Extract HTTP status code
    int httpStatusCode = getIntMetricValue(metricsMap, HttpMetric.HTTP_STATUS_CODE.name());
    Attributes attributes = toHttpAttributes(parentAttributes, httpStatusCode);

    // Report HTTP metrics
    recordMetrics(metricsMap, attributes, httpMetrics);
  }

  private static Map<String, MetricRecord<?>> extractMetrics(MetricCollection metricCollection) {
    Map<String, MetricRecord<?>> metricMap = new HashMap<>();
    for (MetricRecord<?> metricRecord : metricCollection) {
      metricMap.put(metricRecord.metric().name(), metricRecord);
    }
    return metricMap;
  }

  private static String getStringMetricValue(Map<String, MetricRecord<?>> metricsMap, String metricName) {
    MetricRecord<?> metricRecord = metricsMap.get(metricName);
    if (metricRecord != null) {
      Object value = metricRecord.value();
      if (value instanceof String) {
        return (String) value;
      }
    }
    return null;
  }

  @SuppressWarnings("SameParameterValue")
  private static boolean getBooleanMetricValue(Map<String, MetricRecord<?>> metricsMap,
      String metricName) {
    MetricRecord<?> metricRecord = metricsMap.get(metricName);
    if (metricRecord != null) {
      Object value = metricRecord.value();
      if (value instanceof Boolean) {
        return (Boolean) value;
      }
    }
    return false;
  }

  private static int getIntMetricValue(Map<String, MetricRecord<?>> metricsMap, String metricName) {
    MetricRecord<?> metricRecord = metricsMap.get(metricName);
    if (metricRecord != null) {
      Object value = metricRecord.value();
      if (value instanceof Number) {
        return ((Number) value).intValue();
      }
    }
    return 0;
  }

  private Attributes toPerRequestAttributes(String operationName, boolean isSuccess,
      int retryCount) {
    String nullSafeOperationName = operationName == null ? "null" : operationName;
    return perRequestAttributesCache
        .computeIfAbsent(nullSafeOperationName, op -> new ConcurrentHashMap<>())
        .computeIfAbsent(isSuccess, success -> new ConcurrentHashMap<>())
        .computeIfAbsent(retryCount, rc -> Attributes.builder()
            .put("request_operation_name", nullSafeOperationName)
            .put("request_is_success", isSuccess)
            .put("request_retry_count", retryCount)
            .putAll(this.baseAttributes)
            .build());
  }

  private Attributes toAttemptAttributes(Attributes parentAttributes, String errorType) {
    String safeErrorType = errorType == null ? "no_error" : errorType;
    return perAttemptAttributesCache
        .computeIfAbsent(parentAttributes, attr -> new ConcurrentHashMap<>())
        .computeIfAbsent(safeErrorType, type ->
            Attributes.builder()
                .putAll(parentAttributes)
                .put("attempt_error_type", type)
                .build());
  }

  private Attributes toHttpAttributes(Attributes parentAttributes, int httpStatusCode) {
    return perHttpAttributesCache
        .computeIfAbsent(parentAttributes, attr -> new ConcurrentHashMap<>())
        .computeIfAbsent(httpStatusCode, code ->
            Attributes.builder()
                .putAll(parentAttributes)
                .put("http_status_code", code)
                .build());
  }
}
