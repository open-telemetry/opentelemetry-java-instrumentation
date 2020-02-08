package io.opentelemetry.helpers.core;

/** Default implementation of {@link MessageMetadataExtractor} which does nothing. */
public class DefaultMessageMetadataExtractor implements MessageMetadataExtractor {

  @Override
  public <M> MessageMetadata extractMetadata(M message) {
    return new MessageMetadata(0L, 0L, null);
  }
}
