package com.datadoghq.trace.impl;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DDSpanBuilderTest {

    private Tracer tracer;

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
        DDSpan span = (DDSpan) tracer.buildSpan(expectedName).start();
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

        DDSpan span = (DDSpan) tracer
                .buildSpan(expectedName)
                .withTag("1", (Boolean) tags.get("1"))
                .withTag("2", (String) tags.get("2"))
                .withTag("3", (Number) tags.get("3"))
                .start();

        assertThat(span.getOperationName()).isEqualTo(expectedName);
        assertThat(span.getTags()).containsAllEntriesOf(tags);

        // with no tag provided

        span = (DDSpan) tracer
                .buildSpan(expectedName)
                .start();

        assertThat(span.getTags()).isNotNull();
        assertThat(span.getTags()).isEmpty();

        // with all custom fields provided
        final String expectedResource = "fakeResource";
        final String expectedService = "fakeService";
        final String expectedType = "fakeType";

        span = (DDSpan) tracer
                .buildSpan(expectedName)
                .withResourceName(expectedResource)
                .withServiceName(expectedService)
                .withErrorFlag()
                .withSpanType(expectedType)
                .start();

        DDSpanContext actualContext = (DDSpanContext) span.context();

        assertThat(actualContext.getResourceName()).isEqualTo(expectedResource);
        assertThat(actualContext.getErrorFlag()).isTrue();
        assertThat(actualContext.getServiceName()).isEqualTo(expectedService);
        assertThat(actualContext.getSpanType()).isEqualTo(expectedType);

    }

    @Test
    public void shouldBuildSpanTimestampInNano() {

        final long expectedTimestamp = 4875178020000L;
        final String expectedName = "fakeName";

        DDSpan span = (DDSpan) tracer
                .buildSpan(expectedName)
                .withStartTimestamp(expectedTimestamp)
                .start();

        assertThat(span.getStartTime()).isEqualTo(expectedTimestamp * 1000000L);

        // auto-timestamp in nanoseconds
        long tick = Clock.systemUTC().millis() * 1000000L;
        span = (DDSpan) tracer
                .buildSpan(expectedName)
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

        final String expectedName = "fakeName";

        DDSpan span = (DDSpan) tracer
                .buildSpan(expectedName)
                .asChildOf(mockedSpan)
                .start();

        DDSpanContext actualContext = (DDSpanContext) span.context();

        assertThat(actualContext.getParentId()).isEqualTo(expectedParentId);

    }


    @Test
    public void shouldInheritOfTheDDParentAttributes() {

        final String expectedName = "fakeName";
        final String expectedServiceName = "fakeServiceName";
        final String expectedResourceName = "fakeResourceName";
        final String expectedBaggageItemKey = "fakeKey";
        final String expectedBaggageItemValue = "fakeValue";

        DDSpan parent = (DDSpan) tracer
                .buildSpan(expectedName)
                .withServiceName(expectedServiceName)
                .withResourceName(expectedResourceName)
                .start();

        parent.setBaggageItem(expectedBaggageItemKey, expectedBaggageItemValue);

        DDSpan span = (DDSpan) tracer
                .buildSpan(expectedName)
                .asChildOf(parent)
                .start();

        assertThat(span.getOperationName()).isEqualTo(expectedName);
        assertThat(span.getBaggageItem(expectedBaggageItemKey)).isEqualTo(expectedBaggageItemValue);
        assertThat(((DDSpanContext) span.context()).getServiceName()).isEqualTo(expectedServiceName);
        assertThat(((DDSpanContext) span.context()).getResourceName()).isNotEqualTo(expectedResourceName);

    }

    @Test
    public void shouldTrackAllSpanInTrace() throws InterruptedException {

        ArrayList<DDSpan> spans = new ArrayList<>();
        final int nbSamples = 10;

        // root (aka spans[0]) is the parent
        // spans[1] has a predictable duration
        // others are just for fun

        DDSpan root = (DDSpan) tracer.buildSpan("fake_O").start();
        spans.add(root);

        long tickStart = Clock.systemUTC().millis();
        spans.add((DDSpan) tracer.buildSpan("fake_" + 1).asChildOf(spans.get(0)).withStartTimestamp(tickStart).start());
        for (int i = 2; i <= 10; i++) {
            spans.add((DDSpan) tracer.buildSpan("fake_" + i).asChildOf(spans.get(i - 1)).start());
        }

        Thread.sleep(300);
        long tickEnd = Clock.systemUTC().millis();

        spans.get(1).finish(tickEnd);

        assertThat(root.getTrace()).hasSize(nbSamples + 1);
        assertThat(root.getTrace()).containsAll(spans);
        assertThat(spans.get((int) (Math.random() * nbSamples)).getTrace()).containsAll(spans);

        root.finish();
        //TODO Check order
        //assertThat(root.getTrace()).containsExactly(spans)
        assertThat(spans.get(1).durationNano).isEqualTo((tickEnd - tickStart) * 1000000L);
        spans.forEach(span -> assertThat(span.getDurationNano()).isNotNull());
        spans.forEach(span -> assertThat(span.getDurationNano()).isNotZero());


    }

}