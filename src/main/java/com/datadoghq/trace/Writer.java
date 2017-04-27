package com.datadoghq.trace;

import java.util.List;

import io.opentracing.Span;

public interface Writer {

    /**
     * Write a trace represented by the entire list of all the finished spans
     * 
     * @param trace
     */
    public void write(List<Span> trace);

    public void close();
}
