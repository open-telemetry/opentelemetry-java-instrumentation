package com.datadoghq.trace.impl;

import io.opentracing.Span;
import org.junit.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.floatThat;
import static org.mockito.Mockito.mock;


public class DDSpanTest {


    @Test
    public void testBaggageItem() {


        DDSpanContext context = new DDSpanContext();

        final String expectedBaggageItemKey = "fakeKey";
        final String expectedBaggageItemValue = "fakeValue";


        DDSpan span = new DDSpan(
                null,
                "fakeName",
                null,
                null,
                context
        );

        assertThat(span.context().baggageItems()).isEmpty();

        span.setBaggageItem(expectedBaggageItemKey, expectedBaggageItemValue);

        assertThat(span.getBaggageItem(expectedBaggageItemKey)).isEqualTo(expectedBaggageItemValue);

    }

    @Test
    public void testGetSetOperationName() {

        final String expectedOperationName1 = "fake";
        final String expectedOperationName2 = "fake";

        DDSpan span = new DDSpan(
                null,
                expectedOperationName1,
                null,
                null,
                null
        );

        assertThat(span.getOperationName()).isEqualTo(expectedOperationName1);

        span.setOperationName(expectedOperationName2);

        assertThat(span.getOperationName()).isEqualTo(expectedOperationName1);
    }

}