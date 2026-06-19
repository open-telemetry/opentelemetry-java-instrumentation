/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.lettuce.core.protocol;

import io.opentelemetry.instrumentation.lettuce.common.LettuceArgSplitter;
import java.util.List;

public final class OtelCommandArgsUtil {

  /**
   * Extract argument {@link List} from {@link CommandArgs} using public API only. Helper classes
   * can be loaded by a different class loader than Lettuce, so package-private field access is not
   * safe even from the same package name.
   */
  public static List<String> getCommandArgs(CommandArgs<?, ?> commandArgs) {
    return LettuceArgSplitter.splitArgs(commandArgs.toCommandString());
  }

  private OtelCommandArgsUtil() {}
}
