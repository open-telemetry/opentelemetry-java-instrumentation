# Instrumentation for Tomcat request handlers

Tomcat support is divided into the following sub-modules:
- `tomcat-common:library` contains the abstract tracer that works with all supported Tomcat versions
- `tomcat-common:javaagent` contains common type instrumentation and advice helper classes used by
  the `javaagent` modules of all supported Tomcat versions
- `tomcat-7.0:javaagent` applies Tomcat request handler instrumentation for versions `[7, 10)`
- `tomcat-10.0:javaagent` applies Tomcat request handler instrumentation for versions `[10,)`

Instrumentations in `tomcat-7.0` and `tomcat-10.0` are mutually exclusive, this is guaranteed by
`tomcat-10.0` instrumentation checking that its `Request` class uses `jakarta.servlet` classes, and
the `tomcat-7.0` module doing the opposite check.
