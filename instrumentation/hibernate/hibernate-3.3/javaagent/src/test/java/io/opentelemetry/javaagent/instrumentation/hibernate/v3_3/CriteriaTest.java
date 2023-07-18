///*
// * Copyright The OpenTelemetry Authors
// * SPDX-License-Identifier: Apache-2.0
// */
//
//package io.opentelemetry.javaagent.instrumentation.hibernate.v3_3;
//
//
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtensionContext;
//import org.junit.jupiter.params.ParameterizedTest;
//import org.junit.jupiter.params.provider.Arguments;
//import org.junit.jupiter.params.provider.ArgumentsProvider;
//import org.junit.jupiter.params.provider.ArgumentsSource;
//import java.util.stream.Stream;
//
//import static org.junit.jupiter.params.provider.Arguments.arguments;
//
//class CriteriaTest extends AbstractHibernateTest {
//
//  @ParameterizedTest
//  @ArgumentsSource(CriteriaArgs.class)
//  void testCriteria() {
//
//  }
//
////  static final class CriteriaArgs implements ArgumentsProvider {
////
////    @Override
////    public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
////      return Stream.of(
////          Arguments.of("list", (Callable c) -> c.list()),
////          Arguments.of("uniqueResult", (YourClass c) -> c.uniqueResult()),
////          Arguments.of("scroll", (YourClass c) -> c.scroll()));
////    }
////  }
//
//
//}
