package io.opentelemetry.instrumentation.camunda.v7_0;

import org.camunda.bpm.engine.delegate.DelegateExecution;

public class Product {

    public void getInfo(DelegateExecution execution) {
        System.out.println("Product Info");
    }
}