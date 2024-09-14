/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics.java17.internal;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class Constants {
  private Constants() {}

  public static final String ONE = "1";
  public static final String HERTZ = "Hz";
  public static final String BYTES = "By";
  public static final String SECONDS = "s";
  public static final String COMMITTED = "committed";
  public static final String RESERVED = "reserved";
  public static final String INITIAL_SIZE = "initialSize";
  public static final String USED = "used";
  public static final String COMMITTED_SIZE = "committedSize";
  public static final String RESERVED_SIZE = "reservedSize";

  public static final String HEAP = "heap";
  public static final String NON_HEAP = "non_heap";
  public static final String NETWORK_MODE_READ = "read";
  public static final String NETWORK_MODE_WRITE = "write";
  public static final String DURATION = "duration";
  public static final String END_OF_MINOR_GC = "end of minor GC";
  public static final String END_OF_MAJOR_GC = "end of major GC";

  public static final String METRIC_NAME_NETWORK_BYTES = "jvm.network.io";
  public static final String METRIC_DESCRIPTION_NETWORK_BYTES = "Network read/write bytes.";
  public static final String METRIC_NAME_NETWORK_DURATION = "jvm.network.time";
  public static final String METRIC_DESCRIPTION_NETWORK_DURATION = "Network read/write duration.";
  public static final String METRIC_NAME_COMMITTED = "jvm.memory.committed";
  public static final String METRIC_DESCRIPTION_COMMITTED = "Measure of memory committed.";
  public static final String METRIC_NAME_MEMORY = "jvm.memory.used";
  public static final String METRIC_DESCRIPTION_MEMORY = "Measure of memory used.";
  public static final String METRIC_NAME_MEMORY_AFTER = "jvm.memory.used_after_last_gc";
  public static final String METRIC_DESCRIPTION_MEMORY_AFTER =
      "Measure of memory used, as measured after the most recent garbage collection event on this pool.";
  public static final String METRIC_NAME_MEMORY_ALLOCATION = "jvm.memory.allocation";
  public static final String METRIC_DESCRIPTION_MEMORY_ALLOCATION =
      "Measure of memory allocations.";
  public static final String METRIC_NAME_MEMORY_INIT = "jvm.memory.init";
  public static final String METRIC_DESCRIPTION_MEMORY_INIT =
      "Measure of initial memory requested.";
  public static final String METRIC_NAME_MEMORY_LIMIT = "jvm.memory.limit";
  public static final String METRIC_DESCRIPTION_MEMORY_LIMIT = "Measure of max obtainable memory.";
  public static final String METRIC_NAME_GC_DURATION = "jvm.gc.duration";
  public static final String METRIC_DESCRIPTION_GC_DURATION =
      "Duration of JVM garbage collection actions.";

  public static final AttributeKey<String> ATTR_THREAD_NAME = AttributeKey.stringKey("thread.name");
  public static final AttributeKey<String> ATTR_ARENA_NAME = AttributeKey.stringKey("arena");
  public static final AttributeKey<String> ATTR_NETWORK_MODE = AttributeKey.stringKey("mode");
  public static final AttributeKey<String> ATTR_MEMORY_TYPE =
      AttributeKey.stringKey("jvm.memory.type");
  public static final AttributeKey<String> ATTR_MEMORY_POOL =
      AttributeKey.stringKey("jvm.memory.pool.name");
  public static final AttributeKey<String> ATTR_GC_NAME = AttributeKey.stringKey("jvm.gc.name");
  public static final AttributeKey<String> ATTR_GC_ACTION = AttributeKey.stringKey("jvm.gc.action");
  public static final AttributeKey<Boolean> ATTR_DAEMON =
      AttributeKey.booleanKey("jvm.thread.daemon");
  public static final Attributes ATTR_PS_EDEN_SPACE =
      Attributes.of(ATTR_MEMORY_TYPE, HEAP, ATTR_MEMORY_POOL, "PS Eden Space");
  public static final Attributes ATTR_PS_SURVIVOR_SPACE =
      Attributes.of(ATTR_MEMORY_TYPE, HEAP, ATTR_MEMORY_POOL, "PS Survivor Space");
  public static final Attributes ATTR_PS_OLD_GEN =
      Attributes.of(ATTR_MEMORY_TYPE, HEAP, ATTR_MEMORY_POOL, "PS Old Gen");
  public static final Attributes ATTR_G1_SURVIVOR_SPACE =
      Attributes.of(ATTR_MEMORY_TYPE, HEAP, ATTR_MEMORY_POOL, "G1 Survivor Space");
  public static final Attributes ATTR_G1_EDEN_SPACE =
      Attributes.of(ATTR_MEMORY_TYPE, HEAP, ATTR_MEMORY_POOL, "G1 Eden Space");
  public static final Attributes ATTR_METASPACE =
      Attributes.of(ATTR_MEMORY_TYPE, NON_HEAP, ATTR_MEMORY_POOL, "Metaspace");
  public static final Attributes ATTR_COMPRESSED_CLASS_SPACE =
      Attributes.of(ATTR_MEMORY_TYPE, NON_HEAP, ATTR_MEMORY_POOL, "Compressed Class Space");
  public static final Attributes ATTR_CODE_CACHE =
      Attributes.of(ATTR_MEMORY_TYPE, NON_HEAP, ATTR_MEMORY_POOL, "CodeCache");

  public static final String UNIT_CLASSES = "{class}";
  public static final String UNIT_THREADS = "{thread}";
  public static final String UNIT_BUFFERS = "{buffer}";
  public static final String UNIT_UTILIZATION = "1";
}
