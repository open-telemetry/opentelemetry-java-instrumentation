package io.opentelemetry.helpers.core

import spock.lang.Specification

class MessageMetadataTest extends Specification {

  def "should wrap provided data"() {
    given:
    def uncompressedSize = 1024L
    def compressedSize = 518L
    def content = "2898734879afd9872348ed8942738fafbc8c76ec07a"
    when:
    def metadata = new MessageMetadata(uncompressedSize, compressedSize, content)
    then:
    metadata.uncompressedSize == uncompressedSize
    metadata.compressedSize == compressedSize
    metadata.content == content
  }
}
