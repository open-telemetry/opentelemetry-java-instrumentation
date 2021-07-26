/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle;

import io.opentelemetry.instrumentation.test.utils.GcUtils;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
import muzzle.TestClasses;

public class MuzzleWeakReferenceTest {

  // Spock holds strong references to all local variables. For weak reference testing we must create
  // our strong references away from Spock in this java class.
  // Even returning a WeakReference<ClassLoader> is enough for spock to create a strong ref.
  public static boolean classLoaderRefIsGarbageCollected() throws InterruptedException {
    ClassLoader loader = new URLClassLoader(new URL[0], null);
    WeakReference<ClassLoader> clRef = new WeakReference<>(loader);
    ReferenceCollector collector = new ReferenceCollector(className -> false);
    collector.collectReferencesFromAdvice(TestClasses.MethodBodyAdvice.class.getName());
    ReferenceMatcher refMatcher =
        new ReferenceMatcher(
            Collections.emptyList(), collector.getReferences(), className -> false);
    refMatcher.getMismatchedReferenceSources(loader);
    loader = null;
    GcUtils.awaitGc(clRef);
    return clRef.get() == null;
  }
}
