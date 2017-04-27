package com.datadoghq.trace.impl;


import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DDTracerTest {


    @Test
    public void testGenerateNewId() {

        DDTracer tracer = new DDTracer();
        long id1 = tracer.generateNewId();
        long id2 = tracer.generateNewId();

        assertThat(id1).isNotNull();
        assertThat(id1).isNotZero();
        assertThat(id1).isNotEqualTo(id2);
    }

}