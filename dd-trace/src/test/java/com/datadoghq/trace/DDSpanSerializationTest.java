package com.datadoghq.trace;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

public class DDSpanSerializationTest {


    ObjectMapper serializer;
    DDSpan span;
    DDActiveSpan activeSpan;

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
                null,
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
        
        activeSpan = new DDActiveSpan(null,100L,context);
        activeSpan.deactivate();
        serializer = new ObjectMapper();
    }

    @Test
    public void test() throws Exception {


        String expected = "{\"meta\":{\"a-baggage\":\"value\",\"k1\":\"v1\"},\"service\":\"service\",\"error\":0,\"type\":\"type\",\"name\":\"operation\",\"duration\":33000,\"resource\":\"operation\",\"start\":100000,\"span_id\":2,\"parent_id\":0,\"trace_id\":1}";
        // FIXME At the moment, just compare the string sizes
        try {
            assertThat(serializer.writeValueAsString(span).length()).isEqualTo(expected.length());
            
        } catch (AssertionError e) {
            assertThat(serializer.writeValueAsString(span)).isEqualTo(expected);
        }
        
//        try {
//            assertThat(serializer.writeValueAsString(activeSpan).length()).isEqualTo(expected.length());
//        } catch (AssertionError e) {
//            assertThat(serializer.writeValueAsString(activeSpan)).isEqualTo(expected);
//        }
    }

}
