package com.datadoghq.trace.impl;

import io.opentracing.Span;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;


public class DDSpanTest {


    @Test(expected = IllegalArgumentException.class)
    public void shouldHaveServiceName() {
        new DDTracer().buildSpan("operationName").start();
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldOperationNameImmutable() {
        Span span = new DDTracer().buildSpan("foo").withServiceName("foo").start();
        span.setOperationName("boom");
    }

    @Test
    public void shouldResourceNameEqualsOperationNameIfNull() {

        final String expectedName = "operationName";

        DDSpan span = new DDTracer().buildSpan(expectedName).withServiceName("foo").start();
        // ResourceName = expectedName
        assertThat(span.getResourceName()).isEqualTo(expectedName);

        // ResourceName = expectedResourceName
        final String expectedResourceName = "fake";
        span = new DDTracer()
                .buildSpan(expectedName)
                .withResourceName(expectedResourceName)
                .withServiceName("foo").start();

        assertThat(span.getResourceName()).isEqualTo(expectedResourceName);

    }
}