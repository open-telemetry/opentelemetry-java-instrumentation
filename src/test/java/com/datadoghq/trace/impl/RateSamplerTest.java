package com.datadoghq.trace.impl;

import com.datadoghq.trace.Sampler;
import com.datadoghq.trace.impl.DDSpan;
import com.datadoghq.trace.impl.RateSampler;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class RateSamplerTest {


    @Test
    public void testRateSampler() {

        DDSpan mockSpan = mock(DDSpan.class);

        final double sampleRate = 0.35;
        final int iterations = 1000;
        Sampler sampler = new RateSampler(sampleRate);

        int kept = 0;

        for (int i = 0; i < iterations; i++) {
            if (sampler.sample(mockSpan)) {
                kept++;
            }
        }

        assertThat(((double) kept / iterations)).isBetween(sampleRate - 0.02, sampleRate + 0.02);

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