
package datadog.trace.common.sampling;

import com.fasterxml.jackson.databind.JsonNode;
import datadog.opentracing.DDSpan;
import datadog.trace.common.DDTraceConfig;
import datadog.trace.common.writer.DDApi.ResponseListener;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
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
  private RateSampler baseSampler = new RateSampler(1.0);

  private final Map<String, RateSampler> serviceRates = new HashMap<String, RateSampler>();

  @Override
  public synchronized boolean sample(DDSpan span) {
    final String serviceName = span.getServiceName();
    final String env = getSpanEnv(span);
    final String key = "service:" + serviceName + ",env:" + env;
    if (serviceRates.containsKey(key)) {
      return serviceRates.get(key).sample(span);
    } else {
      return baseSampler.sample(span);
    }
  }

  private static String getSpanEnv(DDSpan span) {
    return null == span.getTags().get("env") ? "" : String.valueOf(span.getTags().get("env"));
  }

  @Override
  public void onResponse(String endpoint, JsonNode responseJson) {
    JsonNode newServiceRates = responseJson.get("rate_by_service");
    if (null != newServiceRates) {
      log.debug("Update service sampler rates: {} -> {}", endpoint, responseJson);
      synchronized (this) {
        serviceRates.clear();
        Iterator<String> itr = newServiceRates.fieldNames();
        while (itr.hasNext()) {
          final String key = itr.next();
          try {
            final float val = Float.parseFloat(newServiceRates.get(key).toString());
            if (BASE_KEY.equals(key)) {
              baseSampler = new RateSampler(val);
            } else {
              serviceRates.put(key, new RateSampler(val));
            }
          } catch (NumberFormatException nfe) {
            log.debug("Unable to parse new service rate {} -> {}", key, newServiceRates.get(key));
          }
        }
      }
    }
  }

  public static final class Builder {
    public static RateByServiceSampler forConfig(final Properties config) {
      RateByServiceSampler sampler = null;
      if (config != null) {
        final boolean enabled =
            Boolean.parseBoolean(config.getProperty(DDTraceConfig.PRIORITY_SAMPLING));
        if (enabled) {
          sampler = new RateByServiceSampler();
        }
      }
      return sampler;
    }

    private Builder() {}
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

      if (sampleRate <= 0) {
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
      final boolean sample = Math.random() <= this.sampleRate;
      log.debug("{} - Span is sampled: {}", span, sample);
      return sample;
    }

    public double getSampleRate() {
      return this.sampleRate;
    }

    @Override
    public String toString() {
      return "RateSampler { sampleRate=" + sampleRate + " }";
    }
  }
}
