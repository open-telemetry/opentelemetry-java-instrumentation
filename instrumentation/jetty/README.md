# Instrumentation for Jetty request handlers

Jetty support is divided into the following sub-modules:

- `jetty-common:javaagent` contains common type instrumentation and advice helper classes used by
  the `javaagent` modules of all supported Jetty versions
- `jetty-8.0:javaagent` applies Jetty request handler instrumentation for versions `[8, 11)`
- `jetty-11.0:javaagent` applies Jetty request handler instrumentation for versions `[11,12)`
- `jetty-12.0:javaagent` applies Jetty request handler instrumentation for versions `[12,)`
