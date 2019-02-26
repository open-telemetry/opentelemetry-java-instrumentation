package springdata

import groovy.transform.EqualsAndHashCode
import org.springframework.data.annotation.Id
import org.springframework.data.elasticsearch.annotations.Document

@Document(indexName = "test-index")
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
