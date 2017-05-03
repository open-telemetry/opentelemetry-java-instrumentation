package com.datadoghq.trace.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.datadoghq.trace.Writer;

import io.opentracing.Span;


public class DDTracerTest {


    @Test
    public void write() throws Exception {

        Writer writer = mock(Writer.class);
        RateSampler sampler = mock(RateSampler.class);
        DDSpan span = mock(DDSpan.class);

        // Rate 0.5
        when(sampler.sample(any(DDSpan.class)))
                .thenReturn(true)
                .thenReturn(false);

        List<Span> spans = new ArrayList<Span>();
        spans.add(span);
        spans.add(span);
        spans.add(span);

        DDTracer tracer = new DDTracer(writer, sampler);

        tracer.write(spans);
        tracer.write(spans);

        verify(sampler, times(2)).sample(span);
        verify(writer, times(1)).write(spans);

    }

}