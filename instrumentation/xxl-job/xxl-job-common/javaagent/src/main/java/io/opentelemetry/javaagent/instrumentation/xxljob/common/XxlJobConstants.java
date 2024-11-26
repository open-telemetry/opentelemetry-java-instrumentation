/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.xxljob.common;

public final class XxlJobConstants {

  private XxlJobConstants() {}

  public static final String XXL_GLUE_JOB_HANDLER = "com.xxl.job.core.handler.impl.GlueJobHandler";
  public static final String XXL_SCRIPT_JOB_HANDLER =
      "com.xxl.job.core.handler.impl.ScriptJobHandler";
  public static final String XXL_METHOD_JOB_HANDLER =
      "com.xxl.job.core.handler.impl.MethodJobHandler";
}
