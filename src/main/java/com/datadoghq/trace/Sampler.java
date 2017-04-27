package com.datadoghq.trace;


import com.datadoghq.trace.impl.DDSpan;

public interface Sampler {

    boolean sample(DDSpan span);

}
