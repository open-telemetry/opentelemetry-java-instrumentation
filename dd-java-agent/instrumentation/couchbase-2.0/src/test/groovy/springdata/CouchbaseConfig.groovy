package springdata


import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.data.couchbase.config.AbstractCouchbaseConfiguration
import org.springframework.data.couchbase.repository.config.EnableCouchbaseRepositories
import util.AbstractCouchbaseTest

@Configuration
@EnableCouchbaseRepositories(basePackages = "springdata")
@ComponentScan(basePackages = "springdata")
class CouchbaseConfig extends AbstractCouchbaseConfiguration {

  @Override
  protected List<String> getBootstrapHosts() {
    return Collections.singletonList("127.0.0.1")
  }

  @Override
  protected String getBucketName() {
    return AbstractCouchbaseTest.BUCKET_COUCHBASE.name()
  }

  @Override
  protected String getBucketPassword() {
    return AbstractCouchbaseTest.BUCKET_COUCHBASE.password()
  }
}
