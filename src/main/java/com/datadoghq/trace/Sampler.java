package com.datadoghq.trace;


import com.datadoghq.trace.impl.DDSpan;

public interface Sampler {

    public boolean sample(DDSpan span);

}
