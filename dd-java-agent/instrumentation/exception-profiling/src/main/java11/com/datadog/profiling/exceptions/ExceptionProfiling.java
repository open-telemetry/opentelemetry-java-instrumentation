package com.datadog.profiling.exceptions;

import datadog.trace.api.Config;

/**
 * JVM-wide singleton exception profiling service. Uses {@linkplain Config} class to configure
 * itself using either system properties, environment or properties override.
 */
public final class ExceptionProfiling {

  private static final ExceptionProfiling INSTANCE = new ExceptionProfiling(Config.get());

  /**
   * Get a pre-configured shared instance.
   *
   * @return the shared instance
   */
  public static ExceptionProfiling getInstance() {
    return ExceptionProfiling.INSTANCE;
  }

  private final ExceptionHistogram histogram;
  private final ExceptionSampler sampler;

  private ExceptionProfiling(final Config config) {
    this(new ExceptionSampler(config), new ExceptionHistogram(config));
  }

  ExceptionProfiling(final ExceptionSampler sampler, final ExceptionHistogram histogram) {
    this.sampler = sampler;
    this.histogram = histogram;
  }

  public ExceptionSampleEvent process(final Exception e) {
    // always record the exception in histogram
    final boolean firstHit = histogram.record(e);

    final boolean sampled = sampler.sample();
    if (firstHit || sampled) {
      return new ExceptionSampleEvent(e, sampled, firstHit);
    }
    return null;
  }
}
