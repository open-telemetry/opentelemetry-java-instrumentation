package com.datadoghq.trace;

import java.util.List;

import io.opentracing.Span;

/**
 *	A writer is responsible to send collected spans to some place
 */
public interface Writer {

    /**
     * Write a trace represented by the entire list of all the finished spans
     * 
     * @param trace the list of spans to write
     */
    public void write(List<Span> trace);

    /**
     * Indicates to the writer that no future writing will come and it should terminates all connections and tasks
     */
    public void close();
}
