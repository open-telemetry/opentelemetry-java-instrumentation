package com.datadoghq.trace.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentracing.Span;

/**
 * Main DDSpanSerializer: convert spans and traces to proper JSON
 */
public class DDSpanSerializer {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /* (non-Javadoc)
     * @see com.datadoghq.trace.DDSpanSerializer#serialize(io.opentracing.Span)
     */
    public String serialize(Span span) throws JsonProcessingException {
        return objectMapper.writeValueAsString(span);
    }

    /* (non-Javadoc)
     * @see com.datadoghq.trace.DDSpanSerializer#serialize(java.lang.Object)
     */
    public String serialize(Object spans) throws JsonProcessingException {
        return objectMapper.writeValueAsString(spans);
    }

    /* (non-Javadoc)
     * @see com.datadoghq.trace.DDSpanSerializer#deserialize(java.lang.String)
     */
    public io.opentracing.Span deserialize(String str) throws Exception {
        throw new UnsupportedOperationException("Deserialisation of spans is not implemented yet");
    }

}
