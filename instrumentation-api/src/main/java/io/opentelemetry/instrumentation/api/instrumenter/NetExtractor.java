package io.opentelemetry.instrumentation.api.instrumenter;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.checkerframework.checker.nullness.qual.Nullable;

public abstract class NetExtractor<REQUEST, RESPONSE> extends Extractor<REQUEST, RESPONSE> {

  @Override
  final void onStart(AttributesBuilder attributes, REQUEST request) {
    set(attributes, SemanticAttributes.NET_TRANSPORT, transport(request));
    set(attributes, SemanticAttributes.NET_PEER_NAME, peerName(request));
    set(attributes, SemanticAttributes.NET_PEER_PORT, peerPort(request));
  }

  @Override
  final void onEnd(AttributesBuilder attributes, REQUEST request, RESPONSE response) {
    set(attributes, SemanticAttributes.NET_PEER_IP, peerIp(request, response));
  }

  @Nullable
  protected abstract String transport(REQUEST request);

  @Nullable
  protected abstract String peerName(REQUEST request);

  @Nullable
  protected abstract Long peerPort(REQUEST request);

  @Nullable
  protected abstract String peerIp(REQUEST request, RESPONSE response);
}
