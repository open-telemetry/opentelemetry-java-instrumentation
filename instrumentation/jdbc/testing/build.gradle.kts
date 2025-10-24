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

  api("com.h2database:h2:1.3.169")
  api("org.apache.derby:derby:10.6.1.0")
  api("org.hsqldb:hsqldb:2.0.0")

  api("org.apache.tomcat:tomcat-jdbc:7.0.19")
  api("com.zaxxer:HikariCP:2.4.0")
  api("com.mchange:c3p0:0.9.5")
}
