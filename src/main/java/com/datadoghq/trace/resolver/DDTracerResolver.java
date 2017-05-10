package com.datadoghq.trace.resolver;

import com.datadoghq.trace.DDTracer;
import com.datadoghq.trace.sampling.AllSampler;
import com.datadoghq.trace.writer.DDAgentWriter;
import com.google.auto.service.AutoService;
import io.opentracing.Tracer;
import io.opentracing.contrib.tracerresolver.TracerResolver;


@AutoService(TracerResolver.class)
public class DDTracerResolver extends TracerResolver{

    @Override
    protected Tracer resolve() {
        // TODO find a way to close the reader ...
        return new DDTracer(new DDAgentWriter(), new AllSampler());
    }
}
