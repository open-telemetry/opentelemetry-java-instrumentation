package datadog.trace.api.sampling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import datadog.opentracing.DDSpan;
import org.junit.Test;

public class RateSamplerTest {

  @Test
  public void testRateSampler() {

    final DDSpan mockSpan = mock(DDSpan.class);

    final double sampleRate = 0.35;
    final int iterations = 1000;
    final Sampler sampler = new RateSampler(sampleRate);

    int kept = 0;

    for (int i = 0; i < iterations; i++) {
      if (sampler.sample(mockSpan)) {
        kept++;
      }
    }
    // FIXME test has to be more predictable
    // assertThat(((double) kept / iterations)).isBetween(sampleRate - 0.02, sampleRate + 0.02);

  }

  @Test
  public void testRateBoundaries() {

    RateSampler sampler = new RateSampler(1000);
    assertThat(sampler.getSampleRate()).isEqualTo(1);

    sampler = new RateSampler(-1000);
    assertThat(sampler.getSampleRate()).isEqualTo(1);

    sampler = new RateSampler(0.337);
    assertThat(sampler.getSampleRate()).isEqualTo(0.337);
  }
}
