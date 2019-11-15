package springdata

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository

interface DocRepository extends ElasticsearchRepository<Doc, String> {}
