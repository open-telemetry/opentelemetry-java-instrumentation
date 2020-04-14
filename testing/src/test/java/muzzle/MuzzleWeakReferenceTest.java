/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package muzzle;

import io.opentelemetry.auto.tooling.muzzle.Reference;
import io.opentelemetry.auto.tooling.muzzle.ReferenceCreator;
import io.opentelemetry.auto.tooling.muzzle.ReferenceMatcher;
import io.opentelemetry.auto.util.gc.GCUtils;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLClassLoader;

public class MuzzleWeakReferenceTest {
  /*
   * Spock holds strong references to all local variables. For weak reference testing we must create our strong references away from Spock in this java class.
   *
   * Even returning a WeakReference<ClassLoader> is enough for spock to create a strong ref.
   */
  public static boolean classLoaderRefIsGarbageCollected() throws InterruptedException {
    ClassLoader loader = new URLClassLoader(new URL[0], null);
    final WeakReference<ClassLoader> clRef = new WeakReference<>(loader);
    final Reference[] refs =
        ReferenceCreator.createReferencesFrom(
                TestClasses.MethodBodyAdvice.class.getName(),
                MuzzleWeakReferenceTest.class.getClassLoader())
            .values()
            .toArray(new Reference[0]);
    final ReferenceMatcher refMatcher = new ReferenceMatcher(refs);
    refMatcher.getMismatchedReferenceSources(loader);
    loader = null;
    GCUtils.awaitGC(clRef);
    return clRef.get() == null;
  }
}
