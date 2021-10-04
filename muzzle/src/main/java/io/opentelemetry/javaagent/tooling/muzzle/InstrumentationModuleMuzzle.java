/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle;

import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.muzzle.references.ClassRef;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * This interface contains methods that muzzle automatically adds to the {@link
 * InstrumentationModule}. They are not supposed to be used by end-users, only by our own internal
 * code.
 */
public interface InstrumentationModuleMuzzle {

  /**
   * Returns references to helper and library classes used in this module's type instrumentation
   * advices, grouped by {@link ClassRef#getClassName()}.
   */
  Map<String, ClassRef> getMuzzleReferences();

  /** @see #getMuzzleReferences() */
  static Map<String, ClassRef> getMuzzleReferences(InstrumentationModule module) {
    if (module instanceof InstrumentationModuleMuzzle) {
      return ((InstrumentationModuleMuzzle) module).getMuzzleReferences();
    } else {
      return Collections.emptyMap();
    }
  }

  /**
   * Builds the associations between instrumented library classes and instrumentation context
   * classes. Keys (and their subclasses) will be associated with a context class stored in the
   * value.
   */
  // TODO: we should rename the method so that it doesn't mention "Context", but "VirtualField"
  void registerMuzzleContextStoreClasses(InstrumentationContextBuilder builder);

  /**
   * Returns a list of instrumentation helper classes, automatically detected by muzzle during
   * compilation. Those helpers will be injected into the application classloader.
   */
  List<String> getMuzzleHelperClassNames();

  /** @see #getMuzzleHelperClassNames() */
  static List<String> getMuzzleHelperClassNames(InstrumentationModule module) {
    if (module instanceof InstrumentationModuleMuzzle) {
      return ((InstrumentationModuleMuzzle) module).getMuzzleHelperClassNames();
    } else {
      return Collections.emptyList();
    }
  }
}
