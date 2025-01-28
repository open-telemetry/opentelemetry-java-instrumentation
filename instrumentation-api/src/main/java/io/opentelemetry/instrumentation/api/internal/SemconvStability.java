/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import static java.util.Arrays.asList;

import java.util.HashSet;
import java.util.Set;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class SemconvStability {

  private static final boolean emitOldDatabaseSemconv;
  private static final boolean emitStableDatabaseSemconv;
  private static final boolean emitOldMessagingSemconv;
  private static final boolean emitStableMessagingSemconv;

  static {
    boolean oldDatabase = true;
    boolean stableDatabase = false;
    boolean oldMessaging = true;
    boolean stableMessaging = false;

    String value = ConfigPropertiesUtil.getString("otel.semconv-stability.opt-in");
    System.out.println(value);
    if (value != null) {
      Set<String> values = new HashSet<>(asList(value.split(",")));
      if (values.contains("database")) {
        oldDatabase = false;
        stableDatabase = true;
      }
      // no else -- technically it's possible to set "database,database/dup", in which case we
      // should emit both sets of attributes
      if (values.contains("database/dup")) {
        oldDatabase = true;
        stableDatabase = true;
      }
      if (values.contains("messaging")) {
        oldMessaging = false;
        stableMessaging = true;
      }
      // no else -- technically it's possible to set "messaging,messaging/dup", in which case we
      // should emit both sets of attributes
      // and messaging/dup has higher precedence than messaging in case both values are present
      if (values.contains("messaging/dup")) {
        oldMessaging = true;
        stableMessaging = true;
      }
    }

    emitOldDatabaseSemconv = oldDatabase;
    emitStableDatabaseSemconv = stableDatabase;
    emitOldMessagingSemconv = oldMessaging;
    emitStableMessagingSemconv = stableMessaging;
  }

  public static boolean emitOldDatabaseSemconv() {
    return emitOldDatabaseSemconv;
  }

  public static boolean emitStableDatabaseSemconv() {
    return emitStableDatabaseSemconv;
  }

  public static boolean emitOldMessagingSemconv() {
    return emitOldMessagingSemconv;
  }

  public static boolean emitStableMessagingSemconv() {
    return emitStableMessagingSemconv;
  }

  private SemconvStability() {}
}
