# Spring Boot smoke test
Spring Boot application used by smoke tests of OpenTelemetry java agent.
Builds and publishes Docker image containing a trivial Spring MVC application.

This is a separate gradle project, independent from the rest. This was done to allow
to build and publish it only when actually needed and not on every project build.
