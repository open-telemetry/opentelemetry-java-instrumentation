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
}
