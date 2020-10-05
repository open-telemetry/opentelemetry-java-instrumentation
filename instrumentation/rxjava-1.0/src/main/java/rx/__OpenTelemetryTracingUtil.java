/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package rx;

/**
 * This class must be in the rx package in order to access the package accessible onSubscribe field.
 */
public class __OpenTelemetryTracingUtil {
  public static <T> Observable.OnSubscribe<T> extractOnSubscribe(Observable<T> observable) {
    return observable.onSubscribe;
  }
}
