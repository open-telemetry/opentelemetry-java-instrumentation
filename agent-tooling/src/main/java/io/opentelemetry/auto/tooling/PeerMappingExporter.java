/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.opentelemetry.auto.tooling;

import io.opentelemetry.common.AttributeValue;
import io.opentelemetry.common.AttributeValue.Type;
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.trace.Span.Kind;
import io.opentelemetry.trace.SpanId;
import io.opentelemetry.trace.Status;
import io.opentelemetry.trace.TraceFlags;
import io.opentelemetry.trace.TraceId;
import io.opentelemetry.trace.TraceState;
import io.opentelemetry.trace.attributes.SemanticAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class PeerMappingExporter implements SpanExporter {

  private static final String PEER_SERVICE = "peer.service";

  private final SpanExporter delegate;
  private final Map<String, String> endpointPeerMapping;

  PeerMappingExporter(SpanExporter delegate, Map<String, String> endpointPeerMapping) {
    this.delegate = delegate;
    this.endpointPeerMapping = endpointPeerMapping;
  }

  @Override
  public ResultCode export(Collection<SpanData> spans) {
    List<SpanData> decorated = new ArrayList<>(spans.size());

    for (SpanData span : spans) {
      if (span.getAttributes().containsKey(PEER_SERVICE)) {
        decorated.add(span);
      } else {
        final Map<String, AttributeValue> attributes = span.getAttributes();
        String peerService = mapToPeer(attributes.get(SemanticAttributes.NET_PEER_NAME.key()));
        if (peerService == null) {
          peerService = mapToPeer(attributes.get(SemanticAttributes.NET_PEER_IP.key()));
        }

        if (peerService != null) {
          decorated.add(new PeerMappedSpanData(span, peerService));
        } else {
          decorated.add(span);
        }
      }
    }

    return delegate.export(decorated);
  }

  @Override
  public ResultCode flush() {
    return delegate.flush();
  }

  @Override
  public void shutdown() {
    delegate.shutdown();
  }

  private String mapToPeer(AttributeValue attribute) {
    if (attribute == null || attribute.getType() != Type.STRING) {
      return null;
    }

    return endpointPeerMapping.get(attribute.getStringValue());
  }

  // TODO(anuraaga): This is tedious but shouldn't be.
  // https://github.com/open-telemetry/opentelemetry-java/issues/1321
  private static class PeerMappedSpanData implements SpanData {

    private final SpanData delegate;
    private final Map<String, AttributeValue> attributes;

    private PeerMappedSpanData(SpanData delegate, String peerService) {
      this.delegate = delegate;

      final Map<String, AttributeValue> newAttributes = new HashMap<>(delegate.getAttributes());
      newAttributes.put("peer.service", AttributeValue.stringAttributeValue(peerService));
      attributes = Collections.unmodifiableMap(newAttributes);
    }

    @Override
    public TraceId getTraceId() {
      return delegate.getTraceId();
    }

    @Override
    public SpanId getSpanId() {
      return delegate.getSpanId();
    }

    @Override
    public TraceFlags getTraceFlags() {
      return delegate.getTraceFlags();
    }

    @Override
    public TraceState getTraceState() {
      return delegate.getTraceState();
    }

    @Override
    public SpanId getParentSpanId() {
      return delegate.getParentSpanId();
    }

    @Override
    public Resource getResource() {
      return delegate.getResource();
    }

    @Override
    public InstrumentationLibraryInfo getInstrumentationLibraryInfo() {
      return delegate.getInstrumentationLibraryInfo();
    }

    @Override
    public String getName() {
      return delegate.getName();
    }

    @Override
    public Kind getKind() {
      return delegate.getKind();
    }

    @Override
    public long getStartEpochNanos() {
      return delegate.getStartEpochNanos();
    }

    @Override
    public Map<String, AttributeValue> getAttributes() {
      return attributes;
    }

    @Override
    public List<Event> getEvents() {
      return delegate.getEvents();
    }

    @Override
    public List<Link> getLinks() {
      return delegate.getLinks();
    }

    @Override
    public Status getStatus() {
      return delegate.getStatus();
    }

    @Override
    public long getEndEpochNanos() {
      return delegate.getEndEpochNanos();
    }

    @Override
    public boolean getHasRemoteParent() {
      return delegate.getHasRemoteParent();
    }

    @Override
    public boolean getHasEnded() {
      return delegate.getHasEnded();
    }

    @Override
    public int getTotalRecordedEvents() {
      return delegate.getTotalRecordedEvents();
    }

    @Override
    public int getTotalRecordedLinks() {
      return delegate.getTotalRecordedLinks();
    }

    @Override
    public int getTotalAttributeCount() {
      // We added an attribute.
      return delegate.getTotalAttributeCount() + 1;
    }
  }
}
