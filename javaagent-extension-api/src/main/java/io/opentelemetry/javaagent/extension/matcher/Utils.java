/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension.matcher;

import net.bytebuddy.description.type.TypeDefinition;

final class Utils {
  static String safeTypeDefinitionName(TypeDefinition td) {
    try {
      return td.getTypeName();
    } catch (IllegalStateException ex) {
      String message = ex.getMessage();
      if (message.startsWith("Cannot resolve type description for ")) {
        return message.replace("Cannot resolve type description for ", "");
      } else {
        return "?";
      }
    }
  }

  private Utils() {}
}
