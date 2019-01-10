package datadog.trace.tracer.writer;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Collections;
import java.util.Map;
import lombok.EqualsAndHashCode;

/**
 * Holds sample rate for all known services. This is reported by Dadadog agent in response to
 * writing traces.
 */
@EqualsAndHashCode
class SampleRateByService {

  static final SampleRateByService EMPTY_INSTANCE = new SampleRateByService(Collections.EMPTY_MAP);

  private final Map<String, Double> rateByService;

  @JsonCreator
  SampleRateByService(final Map<String, Double> rateByService) {
    this.rateByService = Collections.unmodifiableMap(rateByService);
  }

  public Double getRate(final String service) {
    // TODO: improve logic in this class to handle default value better
    return rateByService.get(service);
  }
}
