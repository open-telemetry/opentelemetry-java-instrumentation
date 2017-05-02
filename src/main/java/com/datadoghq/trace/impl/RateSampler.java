package com.datadoghq.trace.impl;


import com.datadoghq.trace.Sampler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * This sampler sample the traces at a predefined rate.
 * <p>
 * Keep (100 * `sample_rate`)% of the traces.
 * It samples randomly, its main purpose is to reduce the instrumentation footprint.
 */
public class RateSampler implements Sampler {


    private final static Logger logger = LoggerFactory.getLogger(RateSampler.class);
    /**
     * The sample rate used
     */
    private final double sampleRate;

    /**
     * Build an instance of the sampler. The Sample rate is fixed for each instance.
     *
     * @param sampleRate a number [0,1] representing the rate ratio.
     */
    public RateSampler(double sampleRate) {

        if (sampleRate <= 0) {
            sampleRate = 1;
            logger.error("SampleRate is negative or null, disabling the sampler");
        } else if (sampleRate > 1) {
            sampleRate = 1;
        }

        this.sampleRate = sampleRate;
        logger.debug("Initializing the RateSampler, sampleRate: {} %", this.sampleRate * 100);

    }

    @Override
    public boolean sample(DDSpan span) {
        boolean sample = Math.random() <= this.sampleRate;
        logger.debug("{} - Span is sampled: {}", span, sample);
        return sample;
    }

    public double getSampleRate() {
        return this.sampleRate;
    }

}
