gRPC client spans now populate `server.address` and `server.port` from configured channel targets instead of relying only on channel authority. Target parsing covers DNS, Unix domain socket, IPv4, IPv6, and xDS addresses. Direct-address channels fall back to authority.

### Library API

Use `addClientInterceptor` when configuring a `ManagedChannelBuilder`:

```java
GrpcTelemetry telemetry = GrpcTelemetry.create(openTelemetry);
telemetry.addClientInterceptor(channelBuilder);
```

On gRPC 1.64 and newer, this method captures the builder target. Older versions fall back to channel authority. `createClientInterceptor()` remains supported for integrations that accept only a `ClientInterceptor`, but it cannot capture the builder target.

### Compatibility

`GrpcRequest.getLogicalHost()` and `getLogicalPort()` are deprecated. Use `getServerAddress()` and `getServerPort()` instead.
