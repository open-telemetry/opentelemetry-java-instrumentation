package com.datadoghq.trace;


import com.datadoghq.trace.impl.Span;

public interface Sampler {

    public boolean sample(Span span);

}
