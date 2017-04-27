package com.datadoghq.trace;


import com.datadoghq.trace.impl.DDSpan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RateSampler implements Sampler {

    private final static Logger logger = LoggerFactory.getLogger(RateSampler.class);
    private final double sampleRate;

    public RateSampler(double sampleRate) {

        if (sampleRate <= 0) {
            sampleRate = 1;
            logger.error("SampleRate is negative or null, disabling the sampler");
        } else if (sampleRate > 1) {
            sampleRate = 1;
        }

        this.sampleRate = sampleRate;
        logger.debug("Initializing the RateSampler, sampleRate=" + this.sampleRate * 100 + "%");

    }

    @Override
    public boolean sample(DDSpan span) {
        return Math.random() <= this.sampleRate;
    }

}
