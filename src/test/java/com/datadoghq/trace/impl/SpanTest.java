package com.datadoghq.trace.impl;

import org.junit.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;


public class SpanTest {


    @Test
    public void shouldAddBaggageItem(){


        Tracer mockedTracer = mock(Tracer.class);
        SpanContext mockedContext = mock(SpanContext.class);

        final String expectedBaggageItemKey = "fakeKey";
        final String expectedBaggageItemValue = "fakeValue";


        Span span = new Span(
                mockedTracer,
                "fakeName",
                null,
                Optional.empty(),
                mockedContext
        );

        assertThat(span.context().baggageItems()).isEmpty();

        span.setBaggageItem(expectedBaggageItemKey, expectedBaggageItemValue);

        assertThat(span.getBaggageItem(expectedBaggageItemKey)).isEqualTo(expectedBaggageItemValue);

    }

}