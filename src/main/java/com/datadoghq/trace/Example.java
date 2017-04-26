package com.datadoghq.trace;


import com.datadoghq.trace.impl.DDTags;
import com.datadoghq.trace.writer.impl.DDAgentWriter;
import io.opentracing.Span;
import io.opentracing.Tracer;

public class Example {

    public static void main(String[] args) {


        Tracer tracer = new com.datadoghq.trace.impl.Tracer();
        Writer writer = new DDAgentWriter();

        Span parent = tracer
                .buildSpan("hello-world")
                .withTag(DDTags.SERVICE.getKey(), "service-name")
                .start();

        parent.setBaggageItem("a-baggage", "value");
        parent.finish();

        Span child = tracer
                .buildSpan("hello-world")
                .asChildOf(parent)
                .start();

        child.finish();

        writer.write(parent);
        writer.write(child);

        writer.close();

    }
}