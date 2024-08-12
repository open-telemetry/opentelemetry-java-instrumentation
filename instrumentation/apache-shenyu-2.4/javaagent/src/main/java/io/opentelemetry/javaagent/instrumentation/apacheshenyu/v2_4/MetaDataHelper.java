/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apacheshenyu.v2_4;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.LocalRootSpan;
import io.opentelemetry.javaagent.bootstrap.internal.AgentInstrumentationConfig;
import org.apache.shenyu.common.dto.MetaData;

public final class MetaDataHelper {

  /** ID for apache shenyu metadata * */
  private static final AttributeKey<String> META_ID_ATTRIBUTE =
      AttributeKey.stringKey("apache-shenyu.meta.id");

  /** App name for apache shenyu metadata * */
  private static final AttributeKey<String> APP_NAME_ATTRIBUTE =
      AttributeKey.stringKey("apache-shenyu.meta.app-name");

  /** Context path for apache shenyu metadata * */
  private static final AttributeKey<String> CONTEXT_PATH_ATTRIBUTE =
      AttributeKey.stringKey("apache-shenyu.meta.context-path");

  /** Path for apache shenyu metadata * */
  private static final AttributeKey<String> PATH_ATTRIBUTE =
      AttributeKey.stringKey("apache-shenyu.meta.path");

  /** Rpc type for apache shenyu metadata * */
  private static final AttributeKey<String> RPC_TYPE_ATTRIBUTE =
      AttributeKey.stringKey("apache-shenyu.meta.rpc-type");

  /** Service name for apache shenyu metadata * */
  private static final AttributeKey<String> SERVICE_NAME_ATTRIBUTE =
      AttributeKey.stringKey("apache-shenyu.meta.service-name");

  /** Method name for apache shenyu metadata * */
  private static final AttributeKey<String> METHOD_NAME_ATTRIBUTE =
      AttributeKey.stringKey("apache-shenyu.meta.method-name");

  /** Parameter types for apache shenyu metadata * */
  private static final AttributeKey<String> PARAMETER_TYPES_ATTRIBUTE =
      AttributeKey.stringKey("apache-shenyu.meta.param-types");

  /** Rpc extension for apache shenyu metadata * */
  private static final AttributeKey<String> RPC_EXT_ATTRIBUTE =
      AttributeKey.stringKey("apache-shenyu.meta.rpc-ext");

  /** Rpc extension for apache shenyu metadata * */
  private static final AttributeKey<Boolean> META_ENABLED_ATTRIBUTE =
      AttributeKey.booleanKey("apache-shenyu.meta.enabled");

  private static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES;

  static {
    CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
        AgentInstrumentationConfig.get()
            .getBoolean("otel.instrumentation.apache-shenyu.experimental-span-attributes", false);
  }

  private MetaDataHelper() {}

  public static void extractAttributes(MetaData metadata, Context context) {
    if (metadata != null && CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
      Span serverSpan = LocalRootSpan.fromContextOrNull(context);
      if (serverSpan == null) {
        return;
      }
      serverSpan.setAttribute(META_ID_ATTRIBUTE, metadata.getId());
      serverSpan.setAttribute(APP_NAME_ATTRIBUTE, metadata.getAppName());
      serverSpan.setAttribute(CONTEXT_PATH_ATTRIBUTE, metadata.getContextPath());
      serverSpan.setAttribute(PATH_ATTRIBUTE, metadata.getPath());
      serverSpan.setAttribute(RPC_TYPE_ATTRIBUTE, metadata.getRpcType());
      serverSpan.setAttribute(SERVICE_NAME_ATTRIBUTE, metadata.getServiceName());
      serverSpan.setAttribute(METHOD_NAME_ATTRIBUTE, metadata.getMethodName());
      serverSpan.setAttribute(PARAMETER_TYPES_ATTRIBUTE, metadata.getParameterTypes());
      serverSpan.setAttribute(RPC_EXT_ATTRIBUTE, metadata.getRpcExt());
      serverSpan.setAttribute(META_ENABLED_ATTRIBUTE, metadata.getEnabled());
    }
  }
}
