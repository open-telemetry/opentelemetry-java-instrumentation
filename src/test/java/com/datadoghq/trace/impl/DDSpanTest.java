package com.datadoghq.trace.impl;

import org.junit.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;


public class DDSpanTest {


    @Test
    public void shouldAddBaggageItem() {


        Tracer mockedTracer = mock(Tracer.class);
        DDSpanContext mockedContext = mock(DDSpanContext.class);

        final String expectedBaggageItemKey = "fakeKey";
        final String expectedBaggageItemValue = "fakeValue";


        DDSpan span = new DDSpan(
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