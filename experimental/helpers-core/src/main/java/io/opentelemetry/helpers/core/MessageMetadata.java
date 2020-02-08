package io.opentelemetry.helpers.core;

/** Data transfer object with info about a single span message. */
public class MessageMetadata {

  private final long uncompressedSize;
  private final long compressedSize;
  private final String content;

  /**
   * Constructs a metadata object.
   *
   * @param uncompressedSize the uncompressed message size
   * @param compressedSize the compressed message size
   * @param content the message body or context, if available
   */
  public MessageMetadata(long uncompressedSize, long compressedSize, String content) {
    this.uncompressedSize = uncompressedSize;
    this.compressedSize = compressedSize;
    this.content = content;
  }

  public long getUncompressedSize() {
    return uncompressedSize;
  }

  public long getCompressedSize() {
    return compressedSize;
  }

  public String getContent() {
    return content;
  }
}
