package com.datadoghq.trace;

import io.opentracing.Span;

public interface Writer {

    public void write(Span span);

    public void close();
}
