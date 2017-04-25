package com.datadoghq.trace.impl;

import org.assertj.core.data.MapEntry;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

public class SpanBuilderTest {

    Tracer tracer;

    @Before
    public void setUp() throws Exception {
        tracer = new Tracer();
    }


    @After
    public void tearDown() throws Exception {
    }


    @Test
    public void shouldBuilSimpleSpan() {

        final String expectedName = "fakeName";
        Span span = (Span) tracer.buildSpan(expectedName).start();
        assertThat(span.getOperationName()).isEqualTo(expectedName);
    }

    @Test
    public void shouldBuildTaggedSpan() {

        final String expectedName = "fakeName";
        final Map tags = new HashMap<String, Object>() {
            {
                put("1", true);
                put("2", "fakeString");
                put("3", 42.0);
            }
        };

        Span span = (Span) tracer
                .buildSpan(expectedName)
                .withTag("1", (Boolean) tags.get("1"))
                .withTag("2", (String) tags.get("2"))
                .withTag("3", (Number) tags.get("3"))
                .start();

        assertThat(span.getOperationName()).isEqualTo(expectedName);
        assertThat(span.getTags()).containsAllEntriesOf(tags);

        // with no tag provided

        span = (Span) tracer
                .buildSpan(expectedName)
                .start();

        assertThat(span.getTags()).isNotNull();
        assertThat(span.getTags()).isEmpty();


    }

    @Test
    public void shouldBuilSpanTimestampInNano() {

        final long expectedTimestamp = 487517802L * 1000 * 1000;
        final String expectedName = "fakeName";

        Span span = (Span) tracer
                .buildSpan(expectedName)
                .withStartTimestamp(expectedTimestamp)
                .start();

        assertThat(span.getStartTime()).isEqualTo(expectedTimestamp);

        // auto-timestamp in nanoseconds
        long tick = System.nanoTime();
        span = (Span) tracer
                .buildSpan(expectedName)
                .start();

        // between now and now + 100ms
        assertThat(span.getStartTime()).isBetween(tick, tick * 1000 * 100);

    }


}