package datadog.trace.common.sampling;

import datadog.opentracing.DDSpan;
import java.util.regex.Pattern;

public abstract class SamplingRule {
  private final RateSampler sampler;

  public SamplingRule(final RateSampler sampler) {
    this.sampler = sampler;
  }

  public abstract boolean matches(DDSpan span);

  public boolean sample(final DDSpan span) {
    return sampler.sample(span);
  }

  public RateSampler getSampler() {
    return sampler;
  }

  public static class AlwaysMatchesSamplingRule extends SamplingRule {

    public AlwaysMatchesSamplingRule(final RateSampler sampler) {
      super(sampler);
    }

    @Override
    public boolean matches(final DDSpan span) {
      return true;
    }
  }

  public abstract static class PatternMatchSamplingRule extends SamplingRule {
    private final Pattern pattern;

    public PatternMatchSamplingRule(final String regex, final RateSampler sampler) {
      super(sampler);
      this.pattern = Pattern.compile(regex);
    }

    @Override
    public boolean matches(final DDSpan span) {
      final String relevantString = getRelevantString(span);
      return relevantString != null && pattern.matcher(relevantString).matches();
    }

    protected abstract String getRelevantString(DDSpan span);
  }

  public static class ServiceSamplingRule extends PatternMatchSamplingRule {
    public ServiceSamplingRule(final String regex, final RateSampler sampler) {
      super(regex, sampler);
    }

    @Override
    protected String getRelevantString(final DDSpan span) {
      return span.getServiceName();
    }
  }

  public static class OperationSamplingRule extends PatternMatchSamplingRule {
    public OperationSamplingRule(final String regex, final RateSampler sampler) {
      super(regex, sampler);
    }

    @Override
    protected String getRelevantString(final DDSpan span) {
      return span.getOperationName();
    }
  }
}
