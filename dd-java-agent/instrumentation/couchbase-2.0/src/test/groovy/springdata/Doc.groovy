package springdata

import groovy.transform.EqualsAndHashCode
import org.springframework.data.annotation.Id
import org.springframework.data.couchbase.core.mapping.Document

@Document
@EqualsAndHashCode
class Doc {
  @Id
  private String id = "1"
  private String data = "some data"

  String getId() {
    return id
  }

  void setId(String id) {
    this.id = id
  }

  String getData() {
    return data
  }

  void setData(String data) {
    this.data = data
  }
}
