/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.rules.assertions;

import io.opentelemetry.proto.metrics.v1.NumberDataPoint;
import java.util.function.Function;
import java.util.function.Predicate;

/** Matches data point values against comparison predicates. */
@SuppressWarnings("LambdaFunctionalInterface")
public class DataPointMatcher {
  private final Predicate<Number> testingPredicate;
  private final Function<NumberDataPoint, Number> valueExtractor;
  private final String predicateDescription;

  private DataPointMatcher(
      String predicateDescription,
      Predicate<Number> testingPredicate,
      Function<NumberDataPoint, Number> valueExtractor) {
    this.predicateDescription = predicateDescription;
    this.testingPredicate = testingPredicate;
    this.valueExtractor = valueExtractor;
  }

  /**
   * Creates a matcher that verifies whether the data point value is equal to {@code
   * comparisonValue}.
   *
   * @param comparisonValue value that the data point value is compared to
   * @return matcher instance
   */
  public static DataPointMatcher equalTo(double comparisonValue) {
    return new DataPointMatcher(
        "value = " + comparisonValue,
        (v) -> v.doubleValue() == comparisonValue,
        DataPointMatcher::extractDoubleValue);
  }

  /**
   * Creates a matcher that verifies whether the data point value is equal to {@code
   * comparisonValue}.
   *
   * @param comparisonValue value that the data point value is compared to
   * @return matcher instance
   */
  public static DataPointMatcher equalTo(long comparisonValue) {
    return new DataPointMatcher(
        "value = " + comparisonValue,
        (v) -> v.longValue() == comparisonValue,
        DataPointMatcher::extractIntValue);
  }

  /**
   * Creates a matcher that verifies whether the data point value is less than {@code
   * comparisonValue}.
   *
   * @param comparisonValue value that the data point value is compared to
   * @return matcher instance
   */
  public static DataPointMatcher lessThan(double comparisonValue) {
    return new DataPointMatcher(
        "value < " + comparisonValue,
        (v) -> v.doubleValue() < comparisonValue,
        DataPointMatcher::extractDoubleValue);
  }

  /**
   * Creates a matcher that verifies whether the data point value is less than {@code
   * comparisonValue}.
   *
   * @param comparisonValue value that the data point value is compared to
   * @return matcher instance
   */
  public static DataPointMatcher lessThan(long comparisonValue) {
    return new DataPointMatcher(
        "value < " + comparisonValue,
        (v) -> v.longValue() < comparisonValue,
        DataPointMatcher::extractIntValue);
  }

  /**
   * Creates a matcher that verifies whether the data point value is less than or equal to {@code
   * comparisonValue}.
   *
   * @param comparisonValue value that the data point value is compared to
   * @return matcher instance
   */
  public static DataPointMatcher lessThanOrEqual(double comparisonValue) {
    return new DataPointMatcher(
        "value <= " + comparisonValue,
        (v) -> v.doubleValue() <= comparisonValue,
        DataPointMatcher::extractDoubleValue);
  }

  /**
   * Creates a matcher that verifies whether the data point value is less than or equal to {@code
   * comparisonValue}.
   *
   * @param comparisonValue value that the data point value is compared to
   * @return matcher instance
   */
  public static DataPointMatcher lessThanOrEqual(long comparisonValue) {
    return new DataPointMatcher(
        "value <= " + comparisonValue,
        (v) -> v.longValue() <= comparisonValue,
        DataPointMatcher::extractIntValue);
  }

  /**
   * Creates a matcher that verifies whether the data point value is greater than {@code
   * comparisonValue}.
   *
   * @param comparisonValue value that the data point value is compared to
   * @return matcher instance
   */
  public static DataPointMatcher greaterThan(double comparisonValue) {
    return new DataPointMatcher(
        "value > " + comparisonValue,
        (v) -> v.doubleValue() > comparisonValue,
        DataPointMatcher::extractDoubleValue);
  }

  /**
   * Creates a matcher that verifies whether the data point value is greater than {@code
   * comparisonValue}.
   *
   * @param comparisonValue value that the data point value is compared to
   * @return matcher instance
   */
  public static DataPointMatcher greaterThan(long comparisonValue) {
    return new DataPointMatcher(
        "value > " + comparisonValue,
        (v) -> v.longValue() > comparisonValue,
        DataPointMatcher::extractIntValue);
  }

  /**
   * Creates a matcher that verifies whether the data point value is greater than or equal to {@code
   * comparisonValue}.
   *
   * @param comparisonValue value that the data point value is compared to
   * @return matcher instance
   */
  public static DataPointMatcher greaterThanOrEqual(double comparisonValue) {
    return new DataPointMatcher(
        "value >= " + comparisonValue,
        (v) -> v.doubleValue() >= comparisonValue,
        DataPointMatcher::extractDoubleValue);
  }

  /**
   * Creates a matcher that verifies whether the data point value is greater than or equal to {@code
   * comparisonValue}.
   *
   * @param comparisonValue value that the data point value is compared to
   * @return matcher instance
   */
  public static DataPointMatcher greaterThanOrEqual(long comparisonValue) {
    return new DataPointMatcher(
        "value >= " + comparisonValue,
        (v) -> v.longValue() >= comparisonValue,
        DataPointMatcher::extractIntValue);
  }

  @Override
  public String toString() {
    return predicateDescription;
  }

  boolean matches(NumberDataPoint dataPoint) {
    return testingPredicate.test(valueExtractor.apply(dataPoint));
  }

  private static Number extractDoubleValue(NumberDataPoint dataPoint) {
    return dataPoint.getAsDouble();
  }

  private static Number extractIntValue(NumberDataPoint dataPoint) {
    return dataPoint.getAsInt();
  }
}
