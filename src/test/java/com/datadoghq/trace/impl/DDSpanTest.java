package com.datadoghq.trace.impl;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;


public class DDSpanTest {


    @Test(expected = IllegalArgumentException.class)
    public void shouldHaveServiceName() {
        new DDTracer().buildSpan("operationName").start();
    }

}