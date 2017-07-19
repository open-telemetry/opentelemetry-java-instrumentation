package com.datadoghq.trace;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datadoghq.trace.sampling.RateSampler;
import com.datadoghq.trace.writer.Writer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;
import org.junit.Test;

public class DDTracerTest {

  @Test
  public void write() throws Exception {

    final Writer writer = mock(Writer.class);
    final RateSampler sampler = mock(RateSampler.class);
    final DDSpan span = mock(DDSpan.class);

    // Rate 0.5
    when(sampler.sample(any(DDSpan.class))).thenReturn(true).thenReturn(false);

    final Queue<DDBaseSpan<?>> spans = new LinkedList<>();
    spans.add(span);
    spans.add(span);
    spans.add(span);

    final DDTracer tracer = new DDTracer(writer, sampler);

    tracer.write(spans);
    tracer.write(spans);

    verify(sampler, times(2)).sample(span);
    verify(writer, times(1)).write(new ArrayList<>(spans));
  }
}
