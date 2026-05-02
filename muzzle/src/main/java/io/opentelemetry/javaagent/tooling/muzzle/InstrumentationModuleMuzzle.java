/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;

import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.muzzle.references.ClassRef;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

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

  /** See {@link #getMuzzleReferences()}. */
  static Map<String, ClassRef> getMuzzleReferences(InstrumentationModule module) {
    if (module instanceof InstrumentationModuleMuzzle) {
      return ((InstrumentationModuleMuzzle) module).getMuzzleReferences();
    } else {
      return emptyMap();
    }
  }

  /**
   * Builds the associations between instrumented library classes and instrumentation context
   * classes. Keys (and their subclasses) will be associated with a context class stored in the
   * value.
   */
  void registerMuzzleVirtualFields(VirtualFieldMappingsBuilder builder);

  /**
   * Returns a list of instrumentation helper classes, automatically detected by muzzle during
   * compilation. Those helpers will be injected into the application class loader.
   */
  List<String> getMuzzleHelperClassNames();

  /**
   * Returns a concatenation of {@link #getMuzzleHelperClassNames()} and {@link
   * InstrumentationModule#getAdditionalHelperClassNames()}.
   */
  static List<String> getHelperClassNames(InstrumentationModule module) {
    List<String> muzzleHelperClassNames =
        module instanceof InstrumentationModuleMuzzle
            ? ((InstrumentationModuleMuzzle) module).getMuzzleHelperClassNames()
            : emptyList();

    List<String> additionalHelperClassNames = module.getAdditionalHelperClassNames();

    if (additionalHelperClassNames.isEmpty()) {
      return muzzleHelperClassNames;
    }
    if (muzzleHelperClassNames.isEmpty()) {
      return additionalHelperClassNames;
    }

    List<String> result = new ArrayList<>(muzzleHelperClassNames);
    result.addAll(additionalHelperClassNames);
    return result;
  }

  /**
   * Returns {@code true} when helper classes should be loaded into an isolated class loader, {@code
   * false} when they should be injected to the same class loader as the instrumented library, and
   * {@code null} when other means should be used to decide which strategy to use.
   */
  @Nullable
  default Boolean getMuzzleUseIsolatedHelperClasses() {
    return null;
  }
}
