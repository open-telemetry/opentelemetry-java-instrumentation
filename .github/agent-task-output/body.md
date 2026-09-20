Attribute Vert.x SQL 5 queries to the connection options returned by an asynchronous supplier, including reused connections and failed connection attempts. Depends on #19986.

```java
PgBuilder.pool()
    .using(vertx)
    .connectingTo(() -> loadConnectOptions())
    .build();
```

Concurrent queries retain their own target metadata. A query that times out before a connection is selected omits the unresolved target, and supplier completion after the span ends cannot change its attributes. Fixed configurations retain their configured target on timeout.
