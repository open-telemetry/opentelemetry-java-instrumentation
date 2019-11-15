package datadog.trace.common.sampling;

import com.google.common.util.concurrent.RateLimiter;
import datadog.opentracing.DDSpan;
import datadog.trace.api.sampling.PrioritySampling;
import datadog.trace.common.sampling.SamplingRule.AlwaysMatchesSamplingRule;
import datadog.trace.common.sampling.SamplingRule.OperationSamplingRule;
import datadog.trace.common.sampling.SamplingRule.ServiceSamplingRule;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RuleBasedSampler implements Sampler, PrioritySampler {
  private final List<SamplingRule> samplingRules;
  private final PrioritySampler fallbackSampler;
  private final RateLimiter rateLimiter;
  private final double rateLimit;

  public static final String SAMPLING_RULE_RATE = "_dd.rule_psr";
  public static final String SAMPLING_LIMIT_RATE = "_dd.limit_psr";

  public RuleBasedSampler(
      final List<SamplingRule> samplingRules,
      final double rateLimit,
      final PrioritySampler fallbackSampler) {
    this.samplingRules = samplingRules;
    this.fallbackSampler = fallbackSampler;
    rateLimiter = RateLimiter.create(rateLimit);
    this.rateLimit = rateLimit;
  }


  @Override
  public boolean sample(final DDSpan span) {
    return true;
  }

  @Override
  public void setSamplingPriority(final DDSpan span) {
    SamplingRule matchedRule = null;

    for (final SamplingRule samplingRule : samplingRules) {
      if (samplingRule.matches(span)) {
        matchedRule = samplingRule;
        break;
      }
    }

    if (matchedRule == null) {
      fallbackSampler.setSamplingPriority(span);
    } else {
      final boolean priorityWasSet;
      boolean usedRateLimiter = false;

      if (matchedRule.sample(span)) {
        usedRateLimiter = true;
        if (rateLimiter.tryAcquire()) {
          priorityWasSet = span.context().setSamplingPriority(PrioritySampling.SAMPLER_KEEP);
        } else {
          priorityWasSet = span.context().setSamplingPriority(PrioritySampling.SAMPLER_DROP);
        }
      } else {
        priorityWasSet = span.context().setSamplingPriority(PrioritySampling.SAMPLER_DROP);
      }

      // Only set metrics if we actually set the sampling priority
      // We don't know until the call is completed because the lock is internal to DDSpanContext
      if (priorityWasSet) {
        span.context().setMetric(SAMPLING_RULE_RATE, matchedRule.getSampler().getSampleRate());

        if (usedRateLimiter) {
          span.context().setMetric(SAMPLING_LIMIT_RATE, rateLimit);
        }
      }
    }
  }
}
