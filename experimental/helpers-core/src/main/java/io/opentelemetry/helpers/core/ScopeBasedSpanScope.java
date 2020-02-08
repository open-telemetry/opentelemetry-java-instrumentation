package io.opentelemetry.helpers.core;

import io.opentelemetry.context.Scope;
import io.opentelemetry.distributedcontext.DistributedContext;
import io.opentelemetry.metrics.BatchRecorder;
import io.opentelemetry.metrics.MeasureDouble;
import io.opentelemetry.metrics.MeasureLong;
import io.opentelemetry.metrics.Meter;
import io.opentelemetry.trace.AttributeValue;
import io.opentelemetry.trace.EndSpanOptions;
import io.opentelemetry.trace.Span;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Default implementation of {@link SpanScope}.
 *
 * @param <Q> the request or input object
 * @param <P> the response or output object
 */
public class ScopeBasedSpanScope<Q, P> implements SpanScope<Q, P> {

  private static final AttributeValue MSG_EVENT_ATTR_SENT =
      AttributeValue.stringAttributeValue("SENT");
  private static final AttributeValue MSG_EVENT_ATTR_RECEIVED =
      AttributeValue.stringAttributeValue("RECEIVED");

  private final Span span;
  private final Scope scope;
  private final long startTimestamp;
  private final DistributedContext correlationContext;
  private final StatusTranslator<P> statusTranslator;
  private final MessageMetadataExtractor messageMetadataExtractor;
  private final Meter meter;
  private final MeasureDouble spanDuration;
  private final MeasureLong sentBytes;
  private final MeasureLong recdBytes;
  private final AtomicLong sentMessageSize = new AtomicLong();
  private final AtomicLong receiveMessageSize = new AtomicLong();
  private final AtomicLong sentSeqId = new AtomicLong();
  private final AtomicLong receviedSeqId = new AtomicLong();

  /**
   * Constructs a span scope object.
   *
   * @param span the active span
   * @param scope the tracer scope
   * @param startTimestamp when the span started
   * @param corlatContext the current correlation context
   * @param statusTranslator the native to OpenTelemetry status translator or null to use default
   * @param messageMetadataExtractor the message metadata extractor or null to use default
   * @param meter the meter to use in recording span measurements
   * @param spanDuration the span duration measure or null to not record measurements
   * @param sentBytes the span total sent bytes measure or null to not record measurements
   * @param recdBytes the span total received bytes measure or null to not record measurements
   */
  public ScopeBasedSpanScope(
      Span span,
      Scope scope,
      long startTimestamp,
      DistributedContext corlatContext,
      StatusTranslator<P> statusTranslator,
      MessageMetadataExtractor messageMetadataExtractor,
      Meter meter,
      MeasureDouble spanDuration,
      MeasureLong sentBytes,
      MeasureLong recdBytes) {
    assert span != null;
    assert scope != null;
    this.span = span;
    this.scope = scope;
    this.startTimestamp = startTimestamp;
    this.correlationContext = corlatContext;
    this.statusTranslator =
        statusTranslator == null ? new DefaultStatusTranslator<P>() : statusTranslator;
    this.messageMetadataExtractor =
        messageMetadataExtractor == null
            ? new DefaultMessageMetadataExtractor()
            : messageMetadataExtractor;
    this.meter = meter;
    this.spanDuration = spanDuration;
    this.sentBytes = sentBytes;
    this.recdBytes = recdBytes;
  }

  @Override
  public void onPeerConnection(InetSocketAddress remoteConnection) {
    if (remoteConnection != null) {
      onPeerConnection(remoteConnection.getAddress());

      span.setAttribute(SemanticConventions.NET_PEER_NAME, remoteConnection.getHostName());
      span.setAttribute(SemanticConventions.NET_PEER_PORT, remoteConnection.getPort());
    }
  }

  @Override
  public void onPeerConnection(InetAddress remoteAddress) {
    if (remoteAddress != null) {
      span.setAttribute(SemanticConventions.NET_PEER_NAME, remoteAddress.getHostName());
      span.setAttribute(SemanticConventions.NET_PEER_IP, remoteAddress.getHostAddress());
    }
  }

  @Override
  public <M> M onMessageReceived(M message) {
    MessageMetadata metadata = messageMetadataExtractor.extractMetadata(message);
    receiveMessageSize.addAndGet(metadata.getUncompressedSize());
    recordMessageEvent(receviedSeqId.incrementAndGet(), MSG_EVENT_ATTR_RECEIVED, metadata);
    return message;
  }

  @Override
  public <M> M onMessageSent(M message) {
    MessageMetadata metadata = messageMetadataExtractor.extractMetadata(message);
    sentMessageSize.addAndGet(metadata.getUncompressedSize());
    recordMessageEvent(sentSeqId.incrementAndGet(), MSG_EVENT_ATTR_SENT, metadata);
    return message;
  }

  @Override
  public void onSuccess(P response) {
    span.setStatus(statusTranslator.calculateStatus(null, response));
  }

  @Override
  public void onError(Throwable throwable, P response) {
    assert throwable != null;
    span.setStatus(statusTranslator.calculateStatus(throwable, response));
    recordErrorEvent(throwable);
  }

  @Override
  public Span getSpan() {
    return span;
  }

  @Override
  public DistributedContext getCorrelationContext() {
    return correlationContext;
  }

  @Override
  public void close() {
    EndSpanOptions.Builder builder = EndSpanOptions.builder();
    builder.setEndTimestamp(recordMeasurementsIfConfigured());
    span.end(builder.build());
    scope.close();
  }

  private long recordMeasurementsIfConfigured() {
    long endTimestamp = System.nanoTime();
    if (meter != null && spanDuration != null) {
      recordMeasurements(endTimestamp);
    }
    return endTimestamp;
  }

  private void recordMeasurements(long endTimestamp) {
    BatchRecorder recorder = meter.newMeasureBatchRecorder();
    recorder.put(spanDuration, (endTimestamp - startTimestamp) / 1000000000.0);
    recorder.put(sentBytes, sentMessageSize.longValue());
    recorder.put(recdBytes, receiveMessageSize.longValue());
    recorder.record();
  }

  void recordMessageEvent(long id, AttributeValue type, MessageMetadata metadata) {
    Map<String, AttributeValue> attributes = new HashMap<>();
    attributes.put(SemanticConventions.MESSAGE_TYPE, type);
    attributes.put(SemanticConventions.MESSAGE_ID, AttributeValue.longAttributeValue(id));
    if (metadata.getCompressedSize() > 0L) {
      attributes.put(
          SemanticConventions.MESSAGE_COMPRESSED_SIZE,
          AttributeValue.longAttributeValue(metadata.getCompressedSize()));
    }
    if (metadata.getUncompressedSize() > 0L) {
      attributes.put(
          SemanticConventions.MESSAGE_UNCOMPRESSED_SIZE,
          AttributeValue.longAttributeValue(metadata.getUncompressedSize()));
    }
    if (metadata.getContent() != null) {
      attributes.put(
          SemanticConventions.MESSAGE_CONTENT,
          AttributeValue.stringAttributeValue(metadata.getContent()));
    }
    span.addEvent("message", attributes);
  }

  private void recordErrorEvent(Throwable throwable) {
    Map<String, AttributeValue> attributes = new HashMap<>();
    attributes.put(
        SemanticConventions.ERROR_KIND, AttributeValue.stringAttributeValue("Exception"));
    String msg = throwable.getMessage();
    if (msg == null) {
      msg = throwable.getClass().getSimpleName();
    }
    attributes.put(SemanticConventions.ERROR_MESSAGE, AttributeValue.stringAttributeValue(msg));
    attributes.put(
        SemanticConventions.ERROR_OBJECT,
        AttributeValue.stringAttributeValue(throwable.getClass().getName()));
    span.addEvent("error", attributes);
  }
}
