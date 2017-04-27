package com.datadoghq.trace.impl;

import java.util.ArrayList;
import java.util.List;

import com.datadoghq.trace.SpanSerializer;
import com.datadoghq.trace.writer.impl.DDAgentWriter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.opentracing.Span;

/**
 * Main DDSpanSerializer: convert spans and traces to proper JSON
 */
public class DDSpanSerializer implements SpanSerializer {

    protected final ObjectMapper objectMapper = new ObjectMapper();

    /* (non-Javadoc)
     * @see com.datadoghq.trace.SpanSerializer#serialize(io.opentracing.Span)
     */
    public String serialize(Span span) throws JsonProcessingException {
        return objectMapper.writeValueAsString(span);
    }

    /* (non-Javadoc)
     * @see com.datadoghq.trace.SpanSerializer#serialize(java.lang.Object)
     */
    public String serialize(Object spans) throws JsonProcessingException {
        return objectMapper.writeValueAsString(spans);
    }

    /* (non-Javadoc)
     * @see com.datadoghq.trace.SpanSerializer#deserialize(java.lang.String)
     */
    public io.opentracing.Span deserialize(String str) throws Exception {
        throw new UnsupportedOperationException("Deserialisation of spans is not implemented yet");
    }

    public static void main(String[] args) throws Exception {


        DDAgentWriter writer = new DDAgentWriter();
        DDTracer tracer = new DDTracer(writer, null);

        Span parent = tracer
                .buildSpan("hello-world")
                .withServiceName("service-name")
                .start();

        parent.setBaggageItem("a-baggage", "value");

        Thread.sleep(1000);

        Span child = tracer
                .buildSpan("hello-world")
                .asChildOf(parent)
                .start();

        Thread.sleep(1000);

        child.finish();

        Thread.sleep(1000);

        parent.finish();

        List<List<Span>> traces = new ArrayList<List<Span>>();

        DDSpanSerializer serializer = new DDSpanSerializer();

        System.out.println(serializer.objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(traces));

    }
}
