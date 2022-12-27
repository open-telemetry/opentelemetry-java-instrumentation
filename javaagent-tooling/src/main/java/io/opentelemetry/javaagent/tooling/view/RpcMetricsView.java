/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.view;

import static io.opentelemetry.javaagent.tooling.view.ViewHelper.createView;
import static io.opentelemetry.javaagent.tooling.view.ViewHelper.registerView;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder;
import io.opentelemetry.sdk.metrics.View;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.HashSet;
import java.util.Set;

public final class RpcMetricsView {
  public static final View clientView = buildClientView();
  public static final View serverView = buildServerView();

  private static Set<AttributeKey<?>> buildAlwaysInclude() {
    // the list of recommended metrics attributes is from
    // https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/metrics/semantic_conventions/rpc.md#attributes
    Set<AttributeKey<?>> view = new HashSet<>();
    view.add(SemanticAttributes.RPC_SYSTEM);
    view.add(SemanticAttributes.RPC_SERVICE);
    view.add(SemanticAttributes.RPC_METHOD);
    view.add(SemanticAttributes.RPC_GRPC_STATUS_CODE);
    return view;
  }

  private static View buildClientView() {
    // the list of rpc client metrics attributes is from
    // https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/metrics/semantic_conventions/rpc.md#attributes
    Set<AttributeKey<?>> view = new HashSet<>(buildAlwaysInclude());
    view.add(SemanticAttributes.NET_PEER_NAME);
    view.add(SemanticAttributes.NET_PEER_PORT);
    view.add(SemanticAttributes.NET_TRANSPORT);
    return createView(view);
  }

  private static View buildServerView() {
    // the list of rpc server metrics attributes is from
    // https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/metrics/semantic_conventions/rpc.md#attributes
    Set<AttributeKey<?>> view = new HashSet<>(buildAlwaysInclude());
    view.add(SemanticAttributes.NET_HOST_NAME);
    view.add(SemanticAttributes.NET_SOCK_HOST_ADDR);
    view.add(SemanticAttributes.NET_TRANSPORT);
    return createView(view);
  }

  public static void registerViews(SdkMeterProviderBuilder builder) {
    registerClientView(builder);
    registerServerView(builder);
  }

  private static void registerClientView(SdkMeterProviderBuilder builder) {
    registerView(builder, "rpc.client.duration", clientView);
  }

  private static void registerServerView(SdkMeterProviderBuilder builder) {
    registerView(builder, "rpc.server.duration", serverView);
  }

  private RpcMetricsView() {}
}
