package com.datadoghq.trace.impl;

import com.datadoghq.trace.Sampler;
import com.datadoghq.trace.Writer;
import io.opentracing.Span;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;


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

        List<DDSpan> spans = new ArrayList<DDSpan>();
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