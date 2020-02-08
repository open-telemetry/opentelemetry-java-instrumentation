package io.opentelemetry.helpers.core;

/**
 * Functional interface which defines the extraction of message info from a particular message type.
 */
public interface MessageMetadataExtractor {

  /**
   * Returns the message metadata/info about the supplied message.
   *
   * @param message the message to extract data from
   * @param <M> the message type
   * @return the metadata
   */
  <M> MessageMetadata extractMetadata(M message);
}
