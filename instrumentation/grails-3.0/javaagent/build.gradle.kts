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
    assertInverse.set(true)
  }
}

repositories {
  mavenCentral()
  maven {
    setUrl("https://repo.grails.org/artifactory/core")
    mavenContent {
      releasesOnly()
    }
  }
  mavenLocal()
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

// exclude groovy 4 from testing-common
configurations.configureEach {
  exclude("org.apache.groovy", "groovy")
  exclude("org.apache.groovy", "groovy-json")
}

configurations.configureEach {
  if (!name.contains("muzzle")) {
    resolutionStrategy {
      eachDependency {
        // groovy compilation fails when spock is removed
        // just set spock and groovy to compatible versions
        if (requested.group == "org.spockframework") {
          useVersion("2.1-groovy-3.0")
        }
        if (requested.group == "org.codehaus.groovy") {
          useVersion("3.0.9")
        }
      }
    }
  }
}
