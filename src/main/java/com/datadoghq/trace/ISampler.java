package com.datadoghq.trace;


import com.datadoghq.trace.impl.Span;

public interface ISampler {

    public boolean sample(Span span);

}
