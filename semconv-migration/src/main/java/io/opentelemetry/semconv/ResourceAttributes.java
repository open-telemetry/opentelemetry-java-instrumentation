/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.semconv;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.semconv.incubating.HostIncubatingAttributes;
import io.opentelemetry.semconv.incubating.OsIncubatingAttributes;
import io.opentelemetry.semconv.incubating.ProcessIncubatingAttributes;
import java.util.List;

public final class ResourceAttributes {
  private ResourceAttributes() {}

  public static final String SCHEMA_URL = SchemaUrls.V1_24_0;

  public static final AttributeKey<String> OS_DESCRIPTION = OsIncubatingAttributes.OS_DESCRIPTION;
  public static final AttributeKey<String> OS_TYPE = OsIncubatingAttributes.OS_TYPE;

  public static final class OsTypeValues {
    public static final String WINDOWS = OsIncubatingAttributes.OsTypeValues.WINDOWS;
    public static final String LINUX = OsIncubatingAttributes.OsTypeValues.LINUX;
    public static final String DARWIN = OsIncubatingAttributes.OsTypeValues.DARWIN;
    public static final String FREEBSD = OsIncubatingAttributes.OsTypeValues.FREEBSD;
    public static final String NETBSD = OsIncubatingAttributes.OsTypeValues.NETBSD;
    public static final String OPENBSD = OsIncubatingAttributes.OsTypeValues.OPENBSD;
    public static final String DRAGONFLYBSD = OsIncubatingAttributes.OsTypeValues.DRAGONFLYBSD;
    public static final String HPUX = OsIncubatingAttributes.OsTypeValues.HPUX;
    public static final String AIX = OsIncubatingAttributes.OsTypeValues.AIX;
    public static final String SOLARIS = OsIncubatingAttributes.OsTypeValues.SOLARIS;
    public static final String Z_OS = OsIncubatingAttributes.OsTypeValues.Z_OS;

    private OsTypeValues() {}
  }

  public static final AttributeKey<List<String>> PROCESS_COMMAND_ARGS =
      ProcessIncubatingAttributes.PROCESS_COMMAND_ARGS;
  public static final AttributeKey<String> PROCESS_COMMAND_LINE =
      ProcessIncubatingAttributes.PROCESS_COMMAND_LINE;
  public static final AttributeKey<String> PROCESS_EXECUTABLE_PATH =
      ProcessIncubatingAttributes.PROCESS_EXECUTABLE_PATH;
  public static final AttributeKey<Long> PROCESS_PID = ProcessIncubatingAttributes.PROCESS_PID;
  public static final AttributeKey<String> PROCESS_RUNTIME_DESCRIPTION =
      ProcessIncubatingAttributes.PROCESS_RUNTIME_DESCRIPTION;
  public static final AttributeKey<String> PROCESS_RUNTIME_NAME =
      ProcessIncubatingAttributes.PROCESS_RUNTIME_NAME;
  public static final AttributeKey<String> PROCESS_RUNTIME_VERSION =
      ProcessIncubatingAttributes.PROCESS_RUNTIME_VERSION;

  public static final AttributeKey<String> HOST_ARCH = HostIncubatingAttributes.HOST_ARCH;

  public static final AttributeKey<String> HOST_ID = HostIncubatingAttributes.HOST_ID;

  public static final AttributeKey<String> HOST_NAME = HostIncubatingAttributes.HOST_NAME;




}
