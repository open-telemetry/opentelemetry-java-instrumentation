package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import io.opentelemetry.api.metrics.Meter;
import java.util.function.BiFunction;
import software.amazon.awssdk.core.metrics.CoreMetric;
import software.amazon.awssdk.http.HttpMetric;

/**
 * Catalogue of AWS-SDK metric definitions that this instrumentation recognizes.
 * <p>
 * Each enum constant knows: 
 * (1) the SDK metric identifier
 * (2) the scope in the request/attempt/http hierarchy
 * (3) how to build the {@link MetricStrategy} that records the metric.
 * <p>
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at any time.
 */
public enum MetricSpec {
  // per-request metrics
  API_CALL_DURATION(
      CoreMetric.API_CALL_DURATION.name(),
      Scope.REQUEST,
      (meter, metricPrefix) -> new DurationStrategy(meter, metricPrefix + "api_call_duration", "The total time taken to finish a request (inclusive of all retries)")
  ),
  CREDENTIALS_FETCH_DURATION(
      CoreMetric.CREDENTIALS_FETCH_DURATION.name(),
      Scope.REQUEST,
      (meter, metricPrefix) -> new DurationStrategy(meter, metricPrefix + "credentials_fetch_duration", "Time taken to fetch AWS signing credentials for the request")
  ),
  ENDPOINT_RESOLVE_DURATION(
      CoreMetric.ENDPOINT_RESOLVE_DURATION.name(),
      Scope.REQUEST,
      (meter, metricPrefix) -> new DurationStrategy(meter, metricPrefix + "endpoint_resolve_duration", "Time it took to resolve the endpoint used for the API call")
  ),
  MARSHALLING_DURATION(
      CoreMetric.MARSHALLING_DURATION.name(),
      Scope.REQUEST,
      (meter, metricPrefix) -> new DurationStrategy(meter, metricPrefix + "marshalling_duration", "Time it takes to marshall an SDK request to an HTTP request")
  ),
  TOKEN_FETCH_DURATION(
      CoreMetric.TOKEN_FETCH_DURATION.name(),
      Scope.REQUEST,
      (meter, metricPrefix) -> new DurationStrategy(meter, metricPrefix + "token_fetch_duration", "Time taken to fetch token signing credentials for the request")
  ),

  // per-attempt metrics
  BACKOFF_DELAY_DURATION(
      CoreMetric.BACKOFF_DELAY_DURATION.name(),
      Scope.ATTEMPT,
      (meter, metricPrefix) -> new DurationStrategy(meter, metricPrefix + "backoff_delay_duration", "Duration of time the SDK waited before this API call attempt")
  ),
  READ_THROUGHPUT(
      CoreMetric.READ_THROUGHPUT.name(),
      Scope.ATTEMPT,
      (meter, metricPrefix) -> new DoubleHistogramStrategy(meter, metricPrefix + "read_throughput", "Read throughput of the client in bytes/second")
  ),
  SERVICE_CALL_DURATION(
      CoreMetric.SERVICE_CALL_DURATION.name(),
      Scope.ATTEMPT,
      (meter, metricPrefix) -> new DurationStrategy(meter, metricPrefix + "service_call_duration", "Time to connect, send the request and receive the HTTP status code and header")
  ),
  SIGNING_DURATION(
      CoreMetric.SIGNING_DURATION.name(),
      Scope.ATTEMPT,
      (meter, metricPrefix) -> new DurationStrategy(meter, metricPrefix + "signing_duration", "Time it takes to sign the HTTP request")
  ),
  TIME_TO_FIRST_BYTE(
      CoreMetric.TIME_TO_FIRST_BYTE.name(),
      Scope.ATTEMPT,
      (meter, metricPrefix) -> new DurationStrategy(meter, metricPrefix + "time_to_first_byte", "Elapsed time from sending the HTTP request to receiving the first byte of the headers")
  ),
  TIME_TO_LAST_BYTE(
      CoreMetric.TIME_TO_LAST_BYTE.name(),
      Scope.ATTEMPT,
      (meter, metricPrefix) -> new DurationStrategy(meter, metricPrefix + "time_to_last_byte", "Elapsed time from sending the HTTP request to receiving the last byte of the response")
  ),
  UNMARSHALLING_DURATION(
      CoreMetric.UNMARSHALLING_DURATION.name(),
      Scope.ATTEMPT,
      (meter, metricPrefix) -> new DurationStrategy(meter, metricPrefix + "unmarshalling_duration", "Time it takes to unmarshall an HTTP response to an SDK response")
  ),

  // HTTP metrics
  AVAILABLE_CONCURRENCY(
      HttpMetric.AVAILABLE_CONCURRENCY.name(),
      Scope.HTTP,
      (meter, metricPrefix) -> new LongHistogramStrategy(meter, metricPrefix + "available_concurrency", "Remaining concurrent requests that can be supported without a new connection")
  ),
  CONCURRENCY_ACQUIRE_DURATION(
      HttpMetric.CONCURRENCY_ACQUIRE_DURATION.name(),
      Scope.HTTP,
      (meter, metricPrefix) -> new DurationStrategy(meter, metricPrefix + "concurrency_acquire_duration", "Time taken to acquire a channel from the connection pool")
  ),
  LEASED_CONCURRENCY(
      HttpMetric.LEASED_CONCURRENCY.name(),
      Scope.HTTP,
      (meter, metricPrefix) -> new LongHistogramStrategy(meter, metricPrefix + "leased_concurrency", "Number of requests currently being executed by the HTTP client")
  ),
  MAX_CONCURRENCY(
      HttpMetric.MAX_CONCURRENCY.name(),
      Scope.HTTP,
      (meter, metricPrefix) -> new LongHistogramStrategy(meter, metricPrefix + "max_concurrency", "Maximum number of concurrent requests supported by the HTTP client")
  ),
  PENDING_CONCURRENCY_ACQUIRES(
      HttpMetric.PENDING_CONCURRENCY_ACQUIRES.name(),
      Scope.HTTP,
      (meter, metricPrefix) -> new LongHistogramStrategy(meter, metricPrefix + "pending_concurrency_acquires", "Number of requests waiting for a connection or stream to be available")
  );

  private final String sdkMetricName;
  private final Scope scope;
  @SuppressWarnings("ImmutableEnumChecker")
  private final BiFunction<Meter, String, MetricStrategy> strategyFactory;

  MetricSpec(String sdkMetricName, Scope scope, BiFunction<Meter, String, MetricStrategy> strategyFactory) {
    this.sdkMetricName = sdkMetricName;
    this.scope = scope;
    this.strategyFactory = strategyFactory;
  }

  public String getSdkMetricName() {
    return sdkMetricName;
  }

  public Scope getScope() {
    return scope;
  }

  /** Create a {@link MetricStrategy} for this metric. */
  public MetricStrategy create(Meter meter, String metricPrefix) {
    return strategyFactory.apply(meter, metricPrefix);
  }

  /**
   * Denotes where in the AWS-SDK metric hierarchy the metric lives.
   *
   * This class is internal and is hence not for public use. Its APIs are unstable and can change at any time.
   */
  public enum Scope { REQUEST, ATTEMPT, HTTP }
}
