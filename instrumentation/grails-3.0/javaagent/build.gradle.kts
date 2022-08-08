plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.grails")
    module.set("grails-web-url-mappings")
    versions.set("[3.0,)")
    // version 3.1.15 depends on org.grails:grails-datastore-core:5.0.13.BUILD-SNAPSHOT
    // which (obviously) does not exist
    // version 3.3.6 depends on org.grails:grails-datastore-core:6.1.10.BUILD-SNAPSHOT
    // which (also obviously) does not exist
    skip("3.1.15", "3.3.6")
    // these versions pass if you add the grails maven repository (https://repo.grails.org/artifactory/core)
    skip("3.2.0", "3.3.0", "3.3.1", "3.3.2", "3.3.3", "4.0.0", "4.0.1", "4.0.5", "4.0.6", "4.0.7", "4.0.8", "4.0.9")
    assertInverse.set(true)
  }
}

// first version where our tests work
val grailsVersion = "3.0.6"
val springBootVersion = "1.2.5.RELEASE"

dependencies {
  bootstrap(project(":instrumentation:servlet:servlet-common:bootstrap"))

  library("org.grails:grails-plugin-url-mappings:$grailsVersion")

  testInstrumentation(project(":instrumentation:servlet:servlet-3.0:javaagent"))
  testInstrumentation(project(":instrumentation:servlet:servlet-javax-common:javaagent"))
  testInstrumentation(project(":instrumentation:tomcat:tomcat-7.0:javaagent"))
  testInstrumentation(project(":instrumentation:spring:spring-webmvc-3.1:javaagent"))

  testLibrary("org.springframework.boot:spring-boot-autoconfigure:$springBootVersion")
  testLibrary("org.springframework.boot:spring-boot-starter-tomcat:$springBootVersion")
}

// testing-common pulls in groovy 4 and spock as dependencies, exclude them
configurations.configureEach {
  exclude("org.apache.groovy", "groovy")
  exclude("org.apache.groovy", "groovy-json")
  exclude("org.spockframework", "spock-core")
}

configurations.configureEach {
  if (!name.contains("muzzle")) {
    resolutionStrategy {
      eachDependency {
        if (requested.group == "org.codehaus.groovy") {
          useVersion("3.0.9")
        }
      }
    }
  }
}
