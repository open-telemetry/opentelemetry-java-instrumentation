package com.datadoghq.trace;

import com.datadoghq.trace.impl.Span;

public interface IWriter {

    public void write(Span span);

    public void close();
}
