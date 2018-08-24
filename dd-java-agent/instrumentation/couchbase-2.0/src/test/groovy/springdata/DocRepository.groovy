package springdata

import org.springframework.data.couchbase.repository.CouchbaseRepository

interface DocRepository extends CouchbaseRepository<Doc, String> {}
