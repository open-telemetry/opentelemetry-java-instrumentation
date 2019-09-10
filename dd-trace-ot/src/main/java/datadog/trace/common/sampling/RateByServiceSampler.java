package datadog.trace.common.sampling;

import com.fasterxml.jackson.databind.JsonNode;
import datadog.opentracing.DDSpan;
import datadog.trace.api.sampling.PrioritySampling;
import datadog.trace.common.writer.DDApi.ResponseListener;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * A rate sampler which maintains different sample rates per service+env name.
 *
 * <p>The configuration of (serviceName,env)->rate is configured by the core agent.
 */
@Slf4j
public class RateByServiceSampler implements Sampler, ResponseListener {
  /** Key for setting the baseline rate */
  private static final String BASE_KEY = "service:,env:";
  /** Sampler to use if service+env is not in the map */
  private volatile RateSampler baseSampler = new RateSampler(1.0);

  private volatile Map<String, RateSampler> serviceRates = Collections.emptyMap();

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

    RateSampler sampler = serviceRates.get(key);
    if (sampler == null) {
      sampler = baseSampler;
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
        try {
          final float val = Float.parseFloat(newServiceRates.get(key).toString());
          if (BASE_KEY.equals(key)) {
            baseSampler = new RateSampler(val);
          } else {
            updatedServiceRates.put(key, new RateSampler(val));
          }
        } catch (final NumberFormatException nfe) {
          log.debug("Unable to parse new service rate {} -> {}", key, newServiceRates.get(key));
        }
      }
      serviceRates = Collections.unmodifiableMap(updatedServiceRates);
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

    public RateSampler(final String sampleRate) {
      this(sampleRate == null ? 1 : Double.valueOf(sampleRate));
    }

    /**
     * Build an instance of the sampler. The Sample rate is fixed for each instance.
     *
     * @param sampleRate a number [0,1] representing the rate ratio.
     */
    public RateSampler(double sampleRate) {

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
      final boolean sample = Math.random() <= sampleRate;
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
