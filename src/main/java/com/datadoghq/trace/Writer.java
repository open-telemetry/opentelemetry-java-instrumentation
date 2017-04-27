package com.datadoghq.trace;

import com.datadoghq.trace.impl.DDSpan;

import java.util.List;

/**
 *	A writer is responsible to send collected spans to some place
 */
public interface Writer {

    /**
     * Write a trace represented by the entire list of all the finished spans
     * 
     * @param trace the list of spans to write
     */
    void write(List<DDSpan> trace);

    /**
     * Indicates to the writer that no future writing will come and it should terminates all connections and tasks
     */
    void close();
}
