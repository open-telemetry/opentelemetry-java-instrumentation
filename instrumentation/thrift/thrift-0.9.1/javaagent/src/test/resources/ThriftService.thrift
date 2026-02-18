namespace java io.opentelemetry.javaagent.instrumentation.thrift.v0_9_1.thrift

service ThriftService {
    string sayHello(1:string zone,2:string name);
    string withError();
    void noReturn(1:i32 delay);
    oneway void oneWay();
    oneway void oneWayWithError();
}

