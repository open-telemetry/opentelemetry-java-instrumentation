/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.powerjob.v_4_0;

public final class PowerJobConstants {

  private PowerJobConstants() {}

  public static final String BASIC_PROCESSOR = "BasicProcessor";
  public static final String BROADCAST_PROCESSOR = "BroadcastProcessor";
  public static final String MAP_PROCESSOR = "MapProcessor";
  public static final String MAP_REDUCE_PROCESSOR = "MapReduceProcessor";

  // Official processors
  public static final String SHELL_PROCESSOR = "ShellProcessor";
  public static final String PYTHON_PROCESSOR = "PythonProcessor";
  public static final String HTTP_PROCESSOR = "HttpProcessor";
  public static final String FILE_CLEANUP_PROCESSOR = "FileCleanupProcessor";
  public static final String SPRING_DATASOURCE_SQL_PROCESSOR = "SpringDatasourceSqlProcessor";
  public static final String DYNAMIC_DATASOURCE_SQL_PROCESSOR = "DynamicDatasourceSqlProcessor";
}
