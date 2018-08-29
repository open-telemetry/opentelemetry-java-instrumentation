package springdata

import com.couchbase.client.java.env.CouchbaseEnvironment
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.data.couchbase.config.AbstractCouchbaseConfiguration
import org.springframework.data.couchbase.repository.config.EnableCouchbaseRepositories

import static com.google.common.base.Preconditions.checkNotNull

@Configuration
@EnableCouchbaseRepositories(basePackages = "springdata")
@ComponentScan(basePackages = "springdata")
class CouchbaseConfig extends AbstractCouchbaseConfiguration {

  // This needs to be set before this class can be used by Spring
  static CouchbaseEnvironment environment

  @Override
  protected CouchbaseEnvironment getEnvironment() {
    return checkNotNull(environment)
  }

  @Override
  protected List<String> getBootstrapHosts() {
    return Collections.singletonList("127.0.0.1")
  }

  @Override
  protected String getBucketName() {
    return "CouchbaseSpringRepositoryTest-cb"
  }

  @Override
  protected String getBucketPassword() {
    return "test-pass"
  }

}
