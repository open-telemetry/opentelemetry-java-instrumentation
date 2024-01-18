///*
// * Copyright The OpenTelemetry Authors
// * SPDX-License-Identifier: Apache-2.0
// */
//
//package io.opentelemetry.instrumentation.nifi.v1_24_0;
//
//import io.opentelemetry.instrumentation.nifi.AbstractNifiInstrumentationTest;
//import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
//import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
//import org.junit.jupiter.api.BeforeAll;
//import org.junit.jupiter.api.extension.RegisterExtension;
//
//@SuppressWarnings("unused")
//public class NifiInstrumentationTest extends AbstractNifiInstrumentationTest {
//
//  @RegisterExtension
//  private static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();
//
//  private static NifiTelemetry telemetry;
//
//  @Override
//  protected InstrumentationExtension testing() {
//    return testing;
//  }
//
//  @BeforeAll
//  static void setup() {
//    telemetry = NifiTelemetry.create(testing.getOpenTelemetry());
//  }
//}