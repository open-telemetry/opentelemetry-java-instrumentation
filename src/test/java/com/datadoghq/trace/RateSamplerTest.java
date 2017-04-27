package com.datadoghq.trace;

import com.datadoghq.trace.impl.DDSpan;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class RateSamplerTest {


    @Test
    public void testRateSampler() {

        DDSpan mockSpan = mock(DDSpan.class);

        final double sampleRate = 0.35;
        final int iterations = 100000;
        Sampler sampler = new RateSampler(sampleRate);

        int kept = 0;

        for (int i = 0; i < iterations; i++) {
            if (sampler.sample(mockSpan)) {
                kept++;
            }
        }

        assertThat(((double) kept / iterations)).isBetween(sampleRate - 0.01, sampleRate + 0.01);

    }
}