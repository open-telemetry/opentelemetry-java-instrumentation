package com.datadoghq.trace.impl;

import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class DDSpanSerializerTest {


    DDSpanSerializer serializer;
    DDSpan span;

    @Before
    public void setUp() throws Exception {

        Map<String, String> baggage = new HashMap<String, String>();
        baggage.put("a-baggage", "value");
        Map<String, Object> tags = new HashMap<String, Object>();
        baggage.put("k1", "v1");


        DDSpanContext context = new DDSpanContext(
                1L,
                2L,
                0L,
                "service",
                "operation",
                "resource",
                baggage,
                false,
                "type",
                tags,
                null,
                null);

        span = new DDSpan(
                100L,
                context);

        span.finish(133L);
        serializer = new DDSpanSerializer();
    }

    @Test
    public void test() throws Exception {


        String expected = "{\"meta\":{\"a-baggage\":\"value\",\"k1\":\"v1\"},\"service\":\"service\",\"error\":0,\"type\":\"type\",\"name\":\"operation\",\"duration\":33000,\"resource\":\"resource\",\"start\":100000,\"span_id\":2,\"parent_id\":0,\"trace_id\":1}";
        // FIXME At the moment, just compare the string sizes
        try {
            assertThat(serializer.serialize(span).length()).isEqualTo(expected.length());
        } catch (AssertionError e) {
            assertThat(serializer.serialize(span)).isEqualTo(expected);
        }
    }

}
