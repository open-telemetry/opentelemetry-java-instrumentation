package muzzle;

import datadog.trace.agent.test.TestUtils;
import datadog.trace.agent.tooling.muzzle.Reference;
import datadog.trace.agent.tooling.muzzle.ReferenceCreator;
import datadog.trace.agent.tooling.muzzle.ReferenceMatcher;

import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLClassLoader;

public class MuzzleWeakReferenceTest {
  /*
   * Spock holds strong references to all local variables. For weak reference testing we must create our strong references away from Spock in this java class.
   *
   * Even returning a WeakReference<ClassLoader> is enough for spock to create a strong ref.
   */
  public static boolean classLoaderRefIsGarbageCollected() {
    ClassLoader loader = new URLClassLoader(new URL[0], null);
    WeakReference<ClassLoader> clRef = new WeakReference<>(loader);
    Reference[] refs = ReferenceCreator.createReferencesFrom(TestClasses.MethodBodyAdvice.class.getName(), MuzzleWeakReferenceTest.class.getClassLoader()).values().toArray(new Reference[0]);
    ReferenceMatcher refMatcher = new ReferenceMatcher(refs);
    refMatcher.getMismatchedReferenceSources(loader);
    loader = null;
    TestUtils.awaitGC();
    return clRef.get() == null;
  }
}
