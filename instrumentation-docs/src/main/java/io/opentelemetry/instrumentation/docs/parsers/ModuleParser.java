/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.parsers;

import io.opentelemetry.instrumentation.docs.internal.InstrumentationModule;
import io.opentelemetry.instrumentation.docs.utils.InstrumentationPath;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Handles conversion of InstrumentationPath objects to InstrumentationModule objects. */
public class ModuleParser {

  /**
   * Converts a list of {@link InstrumentationPath} into a list of {@link InstrumentationModule}.
   *
   * @param rootPath the root path for sanitization
   * @param paths the list of {@link InstrumentationPath} objects to be converted
   * @return a list of {@link InstrumentationModule} objects
   */
  public static List<InstrumentationModule> convertToModules(
      String rootPath, List<InstrumentationPath> paths) {
    Map<String, InstrumentationModule> moduleMap = new HashMap<>();

    for (InstrumentationPath path : paths) {
      String moduleKey = createModuleKey(path);
      moduleMap.computeIfAbsent(moduleKey, k -> createModule(rootPath, path));
    }

    return new ArrayList<>(moduleMap.values());
  }

  private static String createModuleKey(InstrumentationPath path) {
    return String.join(":", path.group(), path.namespace(), path.instrumentationName());
  }

  private static InstrumentationModule createModule(String rootPath, InstrumentationPath path) {
    return new InstrumentationModule.Builder()
        .srcPath(sanitizePathName(rootPath, path.srcPath()))
        .instrumentationName(path.instrumentationName())
        .namespace(path.namespace())
        .group(path.group())
        .build();
  }

  // visible for testing
  public static String sanitizePathName(String rootPath, String path) {
    return path.replace(rootPath, "").replace("/javaagent", "").replace("/library", "");
  }

  private ModuleParser() {}
}
