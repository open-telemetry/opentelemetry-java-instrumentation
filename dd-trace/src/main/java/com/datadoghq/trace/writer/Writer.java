package com.datadoghq.trace.writer;

import java.util.List;

import com.datadoghq.trace.DDBaseSpan;

/**
 *	A writer is responsible to send collected spans to some place
 */
public interface Writer {

    /**
     * Write a trace represented by the entire list of all the finished spans
     * 
     * @param trace the list of spans to write
     */
    void write(List<DDBaseSpan<?>> trace);
    
    /**
     * Start the writer
     */
    void start();

    /**
     * Indicates to the writer that no future writing will come and it should terminates all connections and tasks
     */
    void close();
}
