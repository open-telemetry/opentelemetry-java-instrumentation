package datadog.trace.common.sampling;

import static java.util.Collections.singletonMap;
import static java.util.Collections.unmodifiableMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NumericNode;
import datadog.opentracing.DDSpan;
import datadog.trace.api.sampling.PrioritySampling;
import datadog.trace.common.writer.DDApi.ResponseListener;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import lombok.extern.slf4j.Slf4j;

/**
 * A rate sampler which maintains different sample rates per service+env name.
 *
 * <p>The configuration of (serviceName,env)->rate is configured by the core agent.
 */
@Slf4j
public class RateByServiceSampler implements Sampler, ResponseListener {
  /** Key for setting the default/baseline rate */
  private static final String DEFAULT_KEY = "service:,env:";

  private static final double DEFAULT_RATE = 1.0;

  private volatile Map<String, RateSampler> serviceRates =
      unmodifiableMap(singletonMap(DEFAULT_KEY, new RateSampler(DEFAULT_RATE)));

  @Override
  public boolean sample(final DDSpan span) {
    // Priority sampling sends all traces to the core agent, including traces marked dropped.
    // This allows the core agent to collect stats on all traces.
    return true;
  }

  /** If span is a root span, set the span context samplingPriority to keep or drop */
  public void initializeSamplingPriority(final DDSpan span) {
    if (span.isRootSpan()) {
      // Run the priority sampler on the new span
      setSamplingPriorityOnSpanContext(span);
    } else if (span.getSamplingPriority() == null) {
      // Edge case: If the parent context did not set the priority, run the priority sampler.
      // Happens when extracted http context did not send the priority header.
      setSamplingPriorityOnSpanContext(span);
    }
  }

  private void setSamplingPriorityOnSpanContext(final DDSpan span) {
    final String serviceName = span.getServiceName();
    final String env = getSpanEnv(span);
    final String key = "service:" + serviceName + ",env:" + env;

    final Map<String, RateSampler> rates = serviceRates;
    RateSampler sampler = serviceRates.get(key);
    if (sampler == null) {
      sampler = rates.get(DEFAULT_KEY);
    }

    if (sampler.sample(span)) {
      span.setSamplingPriority(PrioritySampling.SAMPLER_KEEP);
    } else {
      span.setSamplingPriority(PrioritySampling.SAMPLER_DROP);
    }
  }

  private static String getSpanEnv(final DDSpan span) {
    return null == span.getTags().get("env") ? "" : String.valueOf(span.getTags().get("env"));
  }

  @Override
  public void onResponse(final String endpoint, final JsonNode responseJson) {
    final JsonNode newServiceRates = responseJson.get("rate_by_service");
    if (null != newServiceRates) {
      log.debug("Update service sampler rates: {} -> {}", endpoint, responseJson);
      final Map<String, RateSampler> updatedServiceRates = new HashMap<>();
      final Iterator<String> itr = newServiceRates.fieldNames();
      while (itr.hasNext()) {
        final String key = itr.next();
        final JsonNode value = newServiceRates.get(key);
        try {
          if (value instanceof NumericNode) {
            updatedServiceRates.put(key, new RateSampler(value.doubleValue()));
          } else {
            log.debug("Unable to parse new service rate {} -> {}", key, value);
          }
        } catch (final NumberFormatException nfe) {
          log.debug("Unable to parse new service rate {} -> {}", key, value);
        }
      }
      if (!updatedServiceRates.containsKey(DEFAULT_KEY)) {
        updatedServiceRates.put(DEFAULT_KEY, new RateSampler(DEFAULT_RATE));
      }
      serviceRates = unmodifiableMap(updatedServiceRates);
    }
  }

  /**
   * This sampler sample the traces at a predefined rate.
   *
   * <p>Keep (100 * `sample_rate`)% of the traces. It samples randomly, its main purpose is to
   * reduce the integration footprint.
   */
  private static class RateSampler extends AbstractSampler {

    /** The sample rate used */
    private final double sampleRate;

    /**
     * Build an instance of the sampler. The Sample rate is fixed for each instance.
     *
     * @param sampleRate a number [0,1] representing the rate ratio.
     */
    private RateSampler(double sampleRate) {

      if (sampleRate < 0) {
        sampleRate = 1;
        log.error("SampleRate is negative or null, disabling the sampler");
      } else if (sampleRate > 1) {
        sampleRate = 1;
      }

      this.sampleRate = sampleRate;
      log.debug("Initializing the RateSampler, sampleRate: {} %", this.sampleRate * 100);
    }

    @Override
    public boolean doSample(final DDSpan span) {
      final boolean sample = ThreadLocalRandom.current().nextFloat() <= sampleRate;
      log.debug("{} - Span is sampled: {}", span, sample);
      return sample;
    }

    public double getSampleRate() {
      return sampleRate;
    }

    @Override
    public String toString() {
      return "RateSampler { sampleRate=" + sampleRate + " }";
    }
  }
}
