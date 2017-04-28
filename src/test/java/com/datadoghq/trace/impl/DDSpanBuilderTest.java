package com.datadoghq.trace.impl;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DDSpanBuilderTest {

    private DDTracer tracer;

    @Before
    public void setUp() throws Exception {
        tracer = new DDTracer();
    }


    @After
    public void tearDown() throws Exception {
    }


    @Test
    public void shouldBuildSimpleSpan() {

        final String expectedName = "fakeName";
        DDSpan span = tracer.buildSpan(expectedName).withServiceName("foo").start();
        assertThat(span.getOperationName()).isEqualTo(expectedName);
    }

    @Test
    public void shouldBuildMoreComplexSpan() {

        final String expectedName = "fakeName";
        final Map tags = new HashMap<String, Object>() {
            {
                put("1", true);
                put("2", "fakeString");
                put("3", 42.0);
            }
        };

        DDSpan span = tracer
                .buildSpan(expectedName)
                .withServiceName("foo")
                .withTag("1", (Boolean) tags.get("1"))
                .withTag("2", (String) tags.get("2"))
                .withTag("3", (Number) tags.get("3"))
                .start();

        assertThat(span.getOperationName()).isEqualTo(expectedName);
        assertThat(span.getTags()).containsAllEntriesOf(tags);

        // with no tag provided

        span = tracer
                .buildSpan(expectedName)
                .withServiceName("foo")
                .start();

        assertThat(span.getTags()).isNotNull();
        assertThat(span.getTags()).isEmpty();

        // with all custom fields provided
        final String expectedResource = "fakeResource";
        final String expectedService = "fakeService";
        final String expectedType = "fakeType";

        span = tracer
                .buildSpan(expectedName)
                .withServiceName("foo")
                .withResourceName(expectedResource)
                .withServiceName(expectedService)
                .withErrorFlag()
                .withSpanType(expectedType)
                .start();

        DDSpanContext actualContext = span.context();

        assertThat(actualContext.getResourceName()).isEqualTo(expectedResource);
        assertThat(actualContext.getErrorFlag()).isTrue();
        assertThat(actualContext.getServiceName()).isEqualTo(expectedService);
        assertThat(actualContext.getSpanType()).isEqualTo(expectedType);

    }

    @Test
    public void shouldBuildSpanTimestampInNano() {

        final long expectedTimestamp = 4875178020000L;
        final String expectedName = "fakeName";

        DDSpan span = tracer
                .buildSpan(expectedName)
                .withServiceName("foo")
                .withStartTimestamp(expectedTimestamp)
                .start();

        assertThat(span.getStartTime()).isEqualTo(expectedTimestamp * 1000000L);

        // auto-timestamp in nanoseconds
        long tick = System.currentTimeMillis() * 1000000L;
        span = tracer
                .buildSpan(expectedName)
                .withServiceName("foo")
                .start();

        // between now and now + 100ms
        assertThat(span.getStartTime()).isBetween(tick, tick + 100 * 1000000L);

    }


    @Test
    public void shouldLinkToParentSpan() {

        final long spanId = 1L;
        final long expectedParentId = spanId;

        DDSpanContext mockedContext = mock(DDSpanContext.class);
        DDSpan mockedSpan = mock(DDSpan.class);

        when(mockedSpan.context()).thenReturn(mockedContext);
        when(mockedContext.getSpanId()).thenReturn(spanId);
        when(mockedContext.getServiceName()).thenReturn("foo");

        final String expectedName = "fakeName";

        DDSpan span = tracer
                .buildSpan(expectedName)
                .withServiceName("foo")
                .asChildOf(mockedSpan)
                .start();

        DDSpanContext actualContext = span.context();

        assertThat(actualContext.getParentId()).isEqualTo(expectedParentId);

    }


    @Test
    public void shouldInheritOfTheDDParentAttributes() {

        final String expectedName = "fakeName";
        final String expectedServiceName = "fakeServiceName";
        final String expectedResourceName = "fakeResourceName";
        final String expectedBaggageItemKey = "fakeKey";
        final String expectedBaggageItemValue = "fakeValue";

        DDSpan parent = tracer
                .buildSpan(expectedName)
                .withServiceName("foo")
                .withServiceName(expectedServiceName)
                .withResourceName(expectedResourceName)
                .start();

        parent.setBaggageItem(expectedBaggageItemKey, expectedBaggageItemValue);

        DDSpan span = tracer
                .buildSpan(expectedName)
                .withServiceName("foo")
                .asChildOf(parent)
                .start();

        assertThat(span.getOperationName()).isEqualTo(expectedName);
        assertThat(span.getBaggageItem(expectedBaggageItemKey)).isEqualTo(expectedBaggageItemValue);
        assertThat(span.context().getServiceName()).isEqualTo(expectedServiceName);
        assertThat(span.context().getResourceName()).isNotEqualTo(expectedResourceName);

    }

    @Test
    public void shouldTrackAllSpanInTrace() throws InterruptedException {

        ArrayList<DDSpan> spans = new ArrayList<DDSpan>();
        final int nbSamples = 10;

        // root (aka spans[0]) is the parent
        // others are just for fun

        DDSpan root = tracer.buildSpan("fake_O").withServiceName("foo").start();
        spans.add(root);


        Thread.sleep(200);
        long tickEnd = System.currentTimeMillis();


        for (int i = 1; i <= 10; i++) {
            spans.add(tracer.buildSpan("fake_" + i).withServiceName("foo").asChildOf(spans.get(i - 1)).start());
        }
        spans.get(1).finish(tickEnd);

        assertThat(root.context.getTrace()).hasSize(nbSamples + 1);
        assertThat(root.context.getTrace()).containsAll(spans);
        assertThat(spans.get((int) (Math.random() * nbSamples)).context.getTrace()).containsAll(spans);



    }

}