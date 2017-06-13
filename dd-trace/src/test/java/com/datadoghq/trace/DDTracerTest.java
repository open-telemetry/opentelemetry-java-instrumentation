package com.datadoghq.trace;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.datadoghq.trace.DDSpan;
import com.datadoghq.trace.DDTracer;
import com.datadoghq.trace.sampling.RateSampler;
import com.datadoghq.trace.writer.Writer;

import io.opentracing.BaseSpan;
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

        List<DDBaseSpan<?>> spans = new ArrayList<DDBaseSpan<?>>();
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