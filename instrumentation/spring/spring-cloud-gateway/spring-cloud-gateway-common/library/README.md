# Shared Library Code for Spring Cloud Gateway Instrumentation

This module contains internal implementation shared by the Spring Cloud Gateway Java agent and
library instrumentation. It is not intended to be used directly by applications.

The `GatewayRouteIdConverger` applies the common route ID cardinality rules so the Java agent and
Spring Boot starter record routes consistently.
