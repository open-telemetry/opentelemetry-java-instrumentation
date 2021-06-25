plugins {
  id("otel.javaagent-testing")
}

// add repo for org.gradle:gradle-tooling-api which org.jboss.shrinkwrap.resolver:shrinkwrap-resolver-gradle-depchain
// which is used by jaxws-2.0-arquillian-testing depends on
repositories {
  mavenCentral()
  maven {
    url 'https://repo.gradle.org/artifactory/libs-releases-local'
    content {
      includeGroup 'org.gradle'
    }
  }
  mavenLocal()
}

dependencies {
  testImplementation project(':instrumentation:jaxws:jaxws-2.0-arquillian-testing')
  testCompileOnly "jakarta.enterprise:jakarta.enterprise.cdi-api:2.0.2"
  testRuntimeOnly "org.apache.tomee:arquillian-tomee-embedded:8.0.6"
  testRuntimeOnly "org.apache.tomee:tomee-embedded:8.0.6"
  testRuntimeOnly "org.apache.tomee:tomee-webservices:8.0.6"

  testInstrumentation project(':instrumentation:servlet:servlet-3.0:javaagent')
  testInstrumentation project(':instrumentation:jaxws:jaxws-2.0:javaagent')
  testInstrumentation project(':instrumentation:jaxws:jaxws-2.0-cxf-3.0:javaagent')
  testInstrumentation project(':instrumentation:jaxws:jws-1.1:javaagent')
}
