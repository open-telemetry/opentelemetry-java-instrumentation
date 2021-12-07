package io.opentelemetry.instrumentation.api.instrumenter.rpc;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * filter metrics unnecessary attributes
 */
public class MetricsView {

  private static final Set<AttributeKey> recommended = buildRecommended();
  private static final Set<AttributeKey> optional = buildOptional();

  private static Set<AttributeKey> buildRecommended() {
    // the list of Recommended metrics attributes is from
    // https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/metrics/semantic_conventions/rpc.md#attributes
    Set<AttributeKey> view = new HashSet<>();
    view.add(SemanticAttributes.RPC_SYSTEM);
    view.add(SemanticAttributes.RPC_SERVICE);
    view.add(SemanticAttributes.RPC_METHOD);
    return view;
  }

  private static Set<AttributeKey> buildOptional() {
    // the list of Recommended metrics attributes is from
    // https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/metrics/semantic_conventions/rpc.md#attributes
    Set<AttributeKey> view = new HashSet<>();
    view.add(SemanticAttributes.NET_PEER_IP);
    view.add(SemanticAttributes.NET_PEER_NAME);
    view.add(SemanticAttributes.NET_PEER_PORT);
    view.add(SemanticAttributes.NET_TRANSPORT);
    return view;
  }

  static Attributes applyRpcView(Attributes startAttributes, Attributes endAttributes) {
    Attributes attributes = startAttributes.toBuilder().putAll(endAttributes).build();
    AttributesBuilder filtered = Attributes.builder();
    applyView(filtered, attributes, recommended);
    applyView(filtered, attributes, optional);
    return filtered.build();
  }

  @SuppressWarnings("unchecked")
  private static void applyView(
      AttributesBuilder filtered, Attributes attributes, Set<AttributeKey> view) {
    attributes.forEach(
        (BiConsumer<AttributeKey, Object>)
            (key, value) -> {
              if (view.contains(key)) {
                filtered.put(key, value);
              }
            });
  }
}
