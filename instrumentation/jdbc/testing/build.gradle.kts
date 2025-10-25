/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

plugins {
  id("otel.java-conventions")
}

dependencies {
  api(project(":testing-common"))
  api("com.google.guava:guava")

  compileOnly("com.h2database:h2:1.3.169")
  compileOnly("org.apache.derby:derby:10.6.1.0")
  compileOnly("org.hsqldb:hsqldb:2.0.0")

  compileOnly("org.apache.tomcat:tomcat-jdbc:7.0.19")
  compileOnly("org.apache.tomcat:tomcat-juli:7.0.19")
  compileOnly("com.zaxxer:HikariCP:2.4.0")
  compileOnly("com.mchange:c3p0:0.9.5")
}
