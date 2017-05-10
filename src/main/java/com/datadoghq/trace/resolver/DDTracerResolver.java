package com.datadoghq.trace.resolver;

import com.datadoghq.trace.DDTracer;
import com.google.auto.service.AutoService;
import io.opentracing.Tracer;
import io.opentracing.contrib.tracerresolver.TracerResolver;


@AutoService(TracerResolver.class)
public class DDTracerResolver extends TracerResolver{

    @Override
    protected Tracer resolve() {
        return new DDTracer();
    }
}
