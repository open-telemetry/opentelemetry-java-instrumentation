/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle;

import io.opentelemetry.instrumentation.test.utils.GcUtils;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLClassLoader;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.TimeoutException;
import muzzle.TestClasses.MethodBodyAdvice;

public class MuzzleWeakReferenceTestUtil {

  // Spock holds strong references to all local variables. For weak reference testing we must create
  // our strong references away from Spock in this java class.
  // Even returning a WeakReference<ClassLoader> is enough for spock to create a strong ref.
  public static boolean classLoaderRefIsGarbageCollected()
      throws InterruptedException, TimeoutException {
    ClassLoader loader = new URLClassLoader(new URL[0], null);
    WeakReference<ClassLoader> clRef = new WeakReference<>(loader);
    ReferenceCollector collector = new ReferenceCollector(className -> false);
    collector.collectReferencesFromAdvice(MethodBodyAdvice.class.getName());
    ReferenceMatcher refMatcher =
        new ReferenceMatcher(
            Collections.emptyList(), collector.getReferences(), className -> false);
    refMatcher.getMismatchedReferenceSources(loader);
    loader = null;
    GcUtils.awaitGc(clRef, Duration.ofSeconds(10));
    return clRef.get() == null;
  }

  private MuzzleWeakReferenceTestUtil() {}
}
