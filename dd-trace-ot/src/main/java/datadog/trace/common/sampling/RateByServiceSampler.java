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
import lombok.extern.slf4j.Slf4j;

/**
 * A rate sampler which maintains different sample rates per service+env name.
 *
 * <p>The configuration of (serviceName,env)->rate is configured by the core agent.
 */
@Slf4j
public class RateByServiceSampler implements Sampler, PrioritySampler, ResponseListener {
  public static final String SAMPLING_AGENT_RATE = "_dd.agent_psr";

  /** Key for setting the default/baseline rate */
  private static final String DEFAULT_KEY = "service:,env:";

  private static final double DEFAULT_RATE = 1.0;

  private volatile Map<String, RateSampler> serviceRates =
      unmodifiableMap(singletonMap(DEFAULT_KEY, createRateSampler(DEFAULT_RATE)));

  @Override
  public boolean sample(final DDSpan span) {
    // Priority sampling sends all traces to the core agent, including traces marked dropped.
    // This allows the core agent to collect stats on all traces.
    return true;
  }

  /** If span is a root span, set the span context samplingPriority to keep or drop */
  @Override
  public void setSamplingPriority(final DDSpan span) {
    final String serviceName = span.getServiceName();
    final String env = getSpanEnv(span);
    final String key = "service:" + serviceName + ",env:" + env;

    final Map<String, RateSampler> rates = serviceRates;
    RateSampler sampler = serviceRates.get(key);
    if (sampler == null) {
      sampler = rates.get(DEFAULT_KEY);
    }

    final boolean priorityWasSet;

    if (sampler.sample(span)) {
      priorityWasSet = span.context().setSamplingPriority(PrioritySampling.SAMPLER_KEEP);
    } else {
      priorityWasSet = span.context().setSamplingPriority(PrioritySampling.SAMPLER_DROP);
    }

    // Only set metrics if we actually set the sampling priority
    // We don't know until the call is completed because the lock is internal to DDSpanContext
    if (priorityWasSet) {
      span.context().setMetric(SAMPLING_AGENT_RATE, sampler.getSampleRate());
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
            updatedServiceRates.put(key, createRateSampler(value.doubleValue()));
          } else {
            log.debug("Unable to parse new service rate {} -> {}", key, value);
          }
        } catch (final NumberFormatException nfe) {
          log.debug("Unable to parse new service rate {} -> {}", key, value);
        }
      }
      if (!updatedServiceRates.containsKey(DEFAULT_KEY)) {
        updatedServiceRates.put(DEFAULT_KEY, createRateSampler(DEFAULT_RATE));
      }
      serviceRates = unmodifiableMap(updatedServiceRates);
    }
  }

  private RateSampler createRateSampler(final double sampleRate) {
    final double sanitizedRate;
    if (sampleRate < 0) {
      log.error("SampleRate is negative or null, disabling the sampler");
      sanitizedRate = 1;
    } else if (sampleRate > 1) {
      sanitizedRate = 1;
    } else {
      sanitizedRate = sampleRate;
    }

    return new DeterministicSampler(sanitizedRate);
  }
}
