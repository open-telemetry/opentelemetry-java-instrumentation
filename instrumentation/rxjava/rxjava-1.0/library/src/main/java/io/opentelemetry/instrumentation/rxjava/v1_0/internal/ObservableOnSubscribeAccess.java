/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rxjava.v1_0.internal;

import java.lang.reflect.Field;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import rx.Observable;

/**
 * Reflective accessor for the package-private {@code rx.Observable#onSubscribe} field.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class ObservableOnSubscribeAccess {

  private static final Logger logger = Logger.getLogger(ObservableOnSubscribeAccess.class.getName());

  @Nullable private static final Field onSubscribeField = findOnSubscribeField();

  @Nullable
  private static Field findOnSubscribeField() {
    try {
      Field field = Observable.class.getDeclaredField("onSubscribe");
      field.setAccessible(true);
      return field;
    } catch (Throwable t) {
      logger.log(Level.WARNING, "Failed to locate rx.Observable#onSubscribe field", t);
      return null;
    }
  }

  // cast is safe: the field is declared as Observable.OnSubscribe<T>
  @SuppressWarnings("unchecked")
  public static <T> Observable.OnSubscribe<T> extractOnSubscribe(Observable<T> observable) {
    if (onSubscribeField == null) {
      throw new IllegalStateException(
          "rx.Observable#onSubscribe field is not accessible; cannot instrument Observable");
    }
    try {
      return (Observable.OnSubscribe<T>) onSubscribeField.get(observable);
    } catch (Throwable t) {
      logger.log(Level.FINE, "Failed to read rx.Observable#onSubscribe field", t);
      throw new IllegalStateException("Failed to read rx.Observable#onSubscribe field", t);
    }
  }

  private ObservableOnSubscribeAccess() {}
}
