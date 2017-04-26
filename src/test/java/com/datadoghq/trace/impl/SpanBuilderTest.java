package com.datadoghq.trace.impl;

import io.opentracing.References;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
    public void shouldBuildSpanTimestampInMilli() {

        final long expectedTimestamp = 487517802L * 1000;
        final String expectedName = "fakeName";

        Span span = (Span) tracer
                .buildSpan(expectedName)
                .withStartTimestamp(expectedTimestamp)
                .start();

        assertThat(span.getStartTime()).isEqualTo(expectedTimestamp);

        // auto-timestamp in nanoseconds
        long tick = System.currentTimeMillis();
        span = (Span) tracer
                .buildSpan(expectedName)
                .start();

        // between now and now + 100ms
        assertThat(span.getStartTime()).isBetween(tick, tick + 100);

    }


    @Test
    public void shouldLinkToParentSpan() {

        final long spanId = 1L;
        final long expectedParentId = spanId;

        SpanContext mockedContext = mock(SpanContext.class);
        Span mockedSpan = mock(Span.class);

        when(mockedSpan.context()).thenReturn(mockedContext);
        when(mockedContext.getSpanId()).thenReturn(spanId);

        final String expectedName = "fakeName";

        Span span = (Span) tracer
                .buildSpan(expectedName)
                .asChildOf(mockedSpan)
                .start();

        SpanContext actualContext = (SpanContext) span.context();

        assertThat(actualContext.getParentId()).isEqualTo(expectedParentId);


    }

    @Test
    public void shouldLinkViaReferenceType() {


        final long spanId = 223L;
        final long expectedParentId = spanId;

        SpanContext mockedContext = mock(SpanContext.class);
        when(mockedContext.getSpanId()).thenReturn(spanId);

        final String expectedName = "fakeName";


        // case 1, using a CHILD_OF ref
        Span span = (Span) tracer
                .buildSpan(expectedName)
                .addReference(References.CHILD_OF, mockedContext)
                .start();

        SpanContext actualContext = (SpanContext) span.context();
        assertThat(actualContext.getParentId()).isEqualTo(expectedParentId);


        // case 2, using a FOLLOW_FROM ref
        span = (Span) tracer
                .buildSpan(expectedName)
                .addReference(References.FOLLOWS_FROM, mockedContext)
                .start();

        actualContext = (SpanContext) span.context();
        assertThat(actualContext.getParentId()).isEqualTo(expectedParentId);

        // case 2, using a WFT ref, should not be linked to the previous
        span = (Span) tracer
                .buildSpan(expectedName)
                .addReference("WTF", mockedContext)
                .start();

        actualContext = (SpanContext) span.context();
        assertThat(actualContext.getParentId()).isEqualTo(0L);

    }

    @Test
    public void shouldInheritOfBaggage() {

        final String expectedName = "fakeName";
        final String expectedBaggageItemKey = "fakeKey";
        final String expectedBaggageItemValue = "fakeValue";

        Span parent = (Span) tracer
                .buildSpan(expectedName)
                .start();

        assertThat(parent.getOperationName()).isEqualTo(expectedName);
        assertThat(parent.context().baggageItems()).isEmpty();

        Span span = (Span) tracer.buildSpan(expectedName).start();

        assertThat(span.getOperationName()).isEqualTo(expectedName);
        assertThat(span.getBaggageItem(expectedBaggageItemKey)).isEqualTo(expectedBaggageItemValue);

    }

}