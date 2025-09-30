///*
// * Copyright The OpenTelemetry Authors
// * SPDX-License-Identifier: Apache-2.0
// */
//
//package io.opentelemetry.javaagent.instrumentation.executors;
//
//import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
//import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
//import java.util.concurrent.ExecutorService;
//import java.util.concurrent.Executors;
//import org.junit.jupiter.api.extension.RegisterExtension;
//
//// TODO: fix this
//class VirtualThreadExecutorQueueWaitTest
//    extends AbstractExecutorServiceQueueWaitTest<ExecutorService, JavaAsyncQueueWaitChild> {
//
//  @RegisterExtension
//  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();
//
//  VirtualThreadExecutorQueueWaitTest() {
//    super(Executors.newVirtualThreadPerTaskExecutor(), testing);
//  }
//
//  @Override
//  protected JavaAsyncQueueWaitChild newTask(Long startXD) {
//    return new JavaAsyncQueueWaitChild(startXD);
//  }
//}
