/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.thrift.v0_9_1.thrift;

import java.util.BitSet;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import org.apache.thrift.EncodingUtils;
import org.apache.thrift.TException;
import org.apache.thrift.async.AsyncMethodCallback;
import org.apache.thrift.protocol.TTupleProtocol;
import org.apache.thrift.scheme.IScheme;
import org.apache.thrift.scheme.SchemeFactory;
import org.apache.thrift.scheme.StandardScheme;
import org.apache.thrift.scheme.TupleScheme;
import org.apache.thrift.server.AbstractNonblockingServer.AsyncFrameBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings({"cast", "rawtypes", "serial", "unchecked", "all"})
public class ThriftService {

  public interface Iface {

    public String sayHello(String zone, String name) throws org.apache.thrift.TException;

    public String withError() throws org.apache.thrift.TException;

    public void noReturn(int delay) throws org.apache.thrift.TException;

    public void oneWay() throws org.apache.thrift.TException;

    public void oneWayWithError() throws org.apache.thrift.TException;
  }

  public interface AsyncIface {

    public void sayHello(
        String zone, String name, org.apache.thrift.async.AsyncMethodCallback resultHandler)
        throws org.apache.thrift.TException;

    public void withError(org.apache.thrift.async.AsyncMethodCallback resultHandler)
        throws org.apache.thrift.TException;

    public void noReturn(int delay, org.apache.thrift.async.AsyncMethodCallback resultHandler)
        throws org.apache.thrift.TException;

    public void oneWay(org.apache.thrift.async.AsyncMethodCallback resultHandler)
        throws org.apache.thrift.TException;

    public void oneWayWithError(org.apache.thrift.async.AsyncMethodCallback resultHandler)
        throws org.apache.thrift.TException;
  }

  public static class Client extends org.apache.thrift.TServiceClient implements Iface {
    public static class Factory implements org.apache.thrift.TServiceClientFactory<Client> {
      public Factory() {}

      public Client getClient(org.apache.thrift.protocol.TProtocol prot) {
        return new Client(prot);
      }

      public Client getClient(
          org.apache.thrift.protocol.TProtocol iprot, org.apache.thrift.protocol.TProtocol oprot) {
        return new Client(iprot, oprot);
      }
    }

    public Client(org.apache.thrift.protocol.TProtocol prot) {
      super(prot, prot);
    }

    public Client(
        org.apache.thrift.protocol.TProtocol iprot, org.apache.thrift.protocol.TProtocol oprot) {
      super(iprot, oprot);
    }

    public String sayHello(String zone, String name) throws org.apache.thrift.TException {
      send_sayHello(zone, name);
      return recv_sayHello();
    }

    public void send_sayHello(String zone, String name) throws org.apache.thrift.TException {
      sayHello_args args = new sayHello_args();
      args.setZone(zone);
      args.setName(name);
      sendBase("sayHello", args);
    }

    public String recv_sayHello() throws org.apache.thrift.TException {
      sayHello_result result = new sayHello_result();
      receiveBase(result, "sayHello");
      if (result.isSetSuccess()) {
        return result.success;
      }
      throw new org.apache.thrift.TApplicationException(
          org.apache.thrift.TApplicationException.MISSING_RESULT,
          "sayHello failed: unknown result");
    }

    public String withError() throws org.apache.thrift.TException {
      send_withError();
      return recv_withError();
    }

    public void send_withError() throws org.apache.thrift.TException {
      withError_args args = new withError_args();
      sendBase("withError", args);
    }

    public String recv_withError() throws org.apache.thrift.TException {
      withError_result result = new withError_result();
      receiveBase(result, "withError");
      if (result.isSetSuccess()) {
        return result.success;
      }
      throw new org.apache.thrift.TApplicationException(
          org.apache.thrift.TApplicationException.MISSING_RESULT,
          "withError failed: unknown result");
    }

    public void noReturn(int delay) throws org.apache.thrift.TException {
      send_noReturn(delay);
      recv_noReturn();
    }

    public void send_noReturn(int delay) throws org.apache.thrift.TException {
      noReturn_args args = new noReturn_args();
      args.setDelay(delay);
      sendBase("noReturn", args);
    }

    public void recv_noReturn() throws org.apache.thrift.TException {
      noReturn_result result = new noReturn_result();
      receiveBase(result, "noReturn");
      return;
    }

    public void oneWay() throws org.apache.thrift.TException {
      send_oneWay();
    }

    public void send_oneWay() throws org.apache.thrift.TException {
      oneWay_args args = new oneWay_args();
      sendBase("oneWay", args);
    }

    public void oneWayWithError() throws org.apache.thrift.TException {
      send_oneWayWithError();
    }

    public void send_oneWayWithError() throws org.apache.thrift.TException {
      oneWayWithError_args args = new oneWayWithError_args();
      sendBase("oneWayWithError", args);
    }
  }

  public static class AsyncClient extends org.apache.thrift.async.TAsyncClient
      implements AsyncIface {
    public static class Factory
        implements org.apache.thrift.async.TAsyncClientFactory<AsyncClient> {
      private org.apache.thrift.async.TAsyncClientManager clientManager;
      private org.apache.thrift.protocol.TProtocolFactory protocolFactory;

      public Factory(
          org.apache.thrift.async.TAsyncClientManager clientManager,
          org.apache.thrift.protocol.TProtocolFactory protocolFactory) {
        this.clientManager = clientManager;
        this.protocolFactory = protocolFactory;
      }

      public AsyncClient getAsyncClient(
          org.apache.thrift.transport.TNonblockingTransport transport) {
        return new AsyncClient(protocolFactory, clientManager, transport);
      }
    }

    public AsyncClient(
        org.apache.thrift.protocol.TProtocolFactory protocolFactory,
        org.apache.thrift.async.TAsyncClientManager clientManager,
        org.apache.thrift.transport.TNonblockingTransport transport) {
      super(protocolFactory, clientManager, transport);
    }

    public void sayHello(
        String zone, String name, org.apache.thrift.async.AsyncMethodCallback resultHandler)
        throws org.apache.thrift.TException {
      checkReady();
      sayHello_call method_call =
          new sayHello_call(zone, name, resultHandler, this, ___protocolFactory, ___transport);
      this.___currentMethod = method_call;
      ___manager.call(method_call);
    }

    public static class sayHello_call extends org.apache.thrift.async.TAsyncMethodCall {
      private String zone;
      private String name;

      public sayHello_call(
          String zone,
          String name,
          org.apache.thrift.async.AsyncMethodCallback resultHandler,
          org.apache.thrift.async.TAsyncClient client,
          org.apache.thrift.protocol.TProtocolFactory protocolFactory,
          org.apache.thrift.transport.TNonblockingTransport transport)
          throws org.apache.thrift.TException {
        super(client, protocolFactory, transport, resultHandler, false);
        this.zone = zone;
        this.name = name;
      }

      public void write_args(org.apache.thrift.protocol.TProtocol prot)
          throws org.apache.thrift.TException {
        prot.writeMessageBegin(
            new org.apache.thrift.protocol.TMessage(
                "sayHello", org.apache.thrift.protocol.TMessageType.CALL, 0));
        sayHello_args args = new sayHello_args();
        args.setZone(zone);
        args.setName(name);
        args.write(prot);
        prot.writeMessageEnd();
      }

      public String getResult() throws org.apache.thrift.TException {
        if (getState() != org.apache.thrift.async.TAsyncMethodCall.State.RESPONSE_READ) {
          throw new IllegalStateException("Method call not finished!");
        }
        org.apache.thrift.transport.TMemoryInputTransport memoryTransport =
            new org.apache.thrift.transport.TMemoryInputTransport(getFrameBuffer().array());
        org.apache.thrift.protocol.TProtocol prot =
            client.getProtocolFactory().getProtocol(memoryTransport);
        return (new Client(prot)).recv_sayHello();
      }
    }

    public void withError(org.apache.thrift.async.AsyncMethodCallback resultHandler)
        throws org.apache.thrift.TException {
      checkReady();
      withError_call method_call =
          new withError_call(resultHandler, this, ___protocolFactory, ___transport);
      this.___currentMethod = method_call;
      ___manager.call(method_call);
    }

    public static class withError_call extends org.apache.thrift.async.TAsyncMethodCall {
      public withError_call(
          org.apache.thrift.async.AsyncMethodCallback resultHandler,
          org.apache.thrift.async.TAsyncClient client,
          org.apache.thrift.protocol.TProtocolFactory protocolFactory,
          org.apache.thrift.transport.TNonblockingTransport transport)
          throws org.apache.thrift.TException {
        super(client, protocolFactory, transport, resultHandler, false);
      }

      public void write_args(org.apache.thrift.protocol.TProtocol prot)
          throws org.apache.thrift.TException {
        prot.writeMessageBegin(
            new org.apache.thrift.protocol.TMessage(
                "withError", org.apache.thrift.protocol.TMessageType.CALL, 0));
        withError_args args = new withError_args();
        args.write(prot);
        prot.writeMessageEnd();
      }

      public String getResult() throws org.apache.thrift.TException {
        if (getState() != org.apache.thrift.async.TAsyncMethodCall.State.RESPONSE_READ) {
          throw new IllegalStateException("Method call not finished!");
        }
        org.apache.thrift.transport.TMemoryInputTransport memoryTransport =
            new org.apache.thrift.transport.TMemoryInputTransport(getFrameBuffer().array());
        org.apache.thrift.protocol.TProtocol prot =
            client.getProtocolFactory().getProtocol(memoryTransport);
        return (new Client(prot)).recv_withError();
      }
    }

    public void noReturn(int delay, org.apache.thrift.async.AsyncMethodCallback resultHandler)
        throws org.apache.thrift.TException {
      checkReady();
      noReturn_call method_call =
          new noReturn_call(delay, resultHandler, this, ___protocolFactory, ___transport);
      this.___currentMethod = method_call;
      ___manager.call(method_call);
    }

    public static class noReturn_call extends org.apache.thrift.async.TAsyncMethodCall {
      private int delay;

      public noReturn_call(
          int delay,
          org.apache.thrift.async.AsyncMethodCallback resultHandler,
          org.apache.thrift.async.TAsyncClient client,
          org.apache.thrift.protocol.TProtocolFactory protocolFactory,
          org.apache.thrift.transport.TNonblockingTransport transport)
          throws org.apache.thrift.TException {
        super(client, protocolFactory, transport, resultHandler, false);
        this.delay = delay;
      }

      public void write_args(org.apache.thrift.protocol.TProtocol prot)
          throws org.apache.thrift.TException {
        prot.writeMessageBegin(
            new org.apache.thrift.protocol.TMessage(
                "noReturn", org.apache.thrift.protocol.TMessageType.CALL, 0));
        noReturn_args args = new noReturn_args();
        args.setDelay(delay);
        args.write(prot);
        prot.writeMessageEnd();
      }

      public void getResult() throws org.apache.thrift.TException {
        if (getState() != org.apache.thrift.async.TAsyncMethodCall.State.RESPONSE_READ) {
          throw new IllegalStateException("Method call not finished!");
        }
        org.apache.thrift.transport.TMemoryInputTransport memoryTransport =
            new org.apache.thrift.transport.TMemoryInputTransport(getFrameBuffer().array());
        org.apache.thrift.protocol.TProtocol prot =
            client.getProtocolFactory().getProtocol(memoryTransport);
        (new Client(prot)).recv_noReturn();
      }
    }

    public void oneWay(org.apache.thrift.async.AsyncMethodCallback resultHandler)
        throws org.apache.thrift.TException {
      checkReady();
      oneWay_call method_call =
          new oneWay_call(resultHandler, this, ___protocolFactory, ___transport);
      this.___currentMethod = method_call;
      ___manager.call(method_call);
    }

    public static class oneWay_call extends org.apache.thrift.async.TAsyncMethodCall {
      public oneWay_call(
          org.apache.thrift.async.AsyncMethodCallback resultHandler,
          org.apache.thrift.async.TAsyncClient client,
          org.apache.thrift.protocol.TProtocolFactory protocolFactory,
          org.apache.thrift.transport.TNonblockingTransport transport)
          throws org.apache.thrift.TException {
        super(client, protocolFactory, transport, resultHandler, true);
      }

      public void write_args(org.apache.thrift.protocol.TProtocol prot)
          throws org.apache.thrift.TException {
        prot.writeMessageBegin(
            new org.apache.thrift.protocol.TMessage(
                "oneWay", org.apache.thrift.protocol.TMessageType.CALL, 0));
        oneWay_args args = new oneWay_args();
        args.write(prot);
        prot.writeMessageEnd();
      }

      public void getResult() throws org.apache.thrift.TException {
        if (getState() != org.apache.thrift.async.TAsyncMethodCall.State.RESPONSE_READ) {
          throw new IllegalStateException("Method call not finished!");
        }
        org.apache.thrift.transport.TMemoryInputTransport memoryTransport =
            new org.apache.thrift.transport.TMemoryInputTransport(getFrameBuffer().array());
        org.apache.thrift.protocol.TProtocol prot =
            client.getProtocolFactory().getProtocol(memoryTransport);
      }
    }

    public void oneWayWithError(org.apache.thrift.async.AsyncMethodCallback resultHandler)
        throws org.apache.thrift.TException {
      checkReady();
      oneWayWithError_call method_call =
          new oneWayWithError_call(resultHandler, this, ___protocolFactory, ___transport);
      this.___currentMethod = method_call;
      ___manager.call(method_call);
    }

    public static class oneWayWithError_call extends org.apache.thrift.async.TAsyncMethodCall {
      public oneWayWithError_call(
          org.apache.thrift.async.AsyncMethodCallback resultHandler,
          org.apache.thrift.async.TAsyncClient client,
          org.apache.thrift.protocol.TProtocolFactory protocolFactory,
          org.apache.thrift.transport.TNonblockingTransport transport)
          throws org.apache.thrift.TException {
        super(client, protocolFactory, transport, resultHandler, true);
      }

      public void write_args(org.apache.thrift.protocol.TProtocol prot)
          throws org.apache.thrift.TException {
        prot.writeMessageBegin(
            new org.apache.thrift.protocol.TMessage(
                "oneWayWithError", org.apache.thrift.protocol.TMessageType.CALL, 0));
        oneWayWithError_args args = new oneWayWithError_args();
        args.write(prot);
        prot.writeMessageEnd();
      }

      public void getResult() throws org.apache.thrift.TException {
        if (getState() != org.apache.thrift.async.TAsyncMethodCall.State.RESPONSE_READ) {
          throw new IllegalStateException("Method call not finished!");
        }
        org.apache.thrift.transport.TMemoryInputTransport memoryTransport =
            new org.apache.thrift.transport.TMemoryInputTransport(getFrameBuffer().array());
        org.apache.thrift.protocol.TProtocol prot =
            client.getProtocolFactory().getProtocol(memoryTransport);
      }
    }
  }

  public static class Processor<I extends Iface> extends org.apache.thrift.TBaseProcessor<I>
      implements org.apache.thrift.TProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(Processor.class.getName());

    public Processor(I iface) {
      super(
          iface,
          getProcessMap(
              new HashMap<
                  String,
                  org.apache.thrift.ProcessFunction<I, ? extends org.apache.thrift.TBase>>()));
    }

    protected Processor(
        I iface,
        Map<String, org.apache.thrift.ProcessFunction<I, ? extends org.apache.thrift.TBase>>
            processMap) {
      super(iface, getProcessMap(processMap));
    }

    private static <I extends Iface>
        Map<String, org.apache.thrift.ProcessFunction<I, ? extends org.apache.thrift.TBase>>
            getProcessMap(
                Map<String, org.apache.thrift.ProcessFunction<I, ? extends org.apache.thrift.TBase>>
                    processMap) {
      processMap.put("sayHello", new sayHello());
      processMap.put("withError", new withError());
      processMap.put("noReturn", new noReturn());
      processMap.put("oneWay", new oneWay());
      processMap.put("oneWayWithError", new oneWayWithError());
      return processMap;
    }

    public static class sayHello<I extends Iface>
        extends org.apache.thrift.ProcessFunction<I, sayHello_args> {
      public sayHello() {
        super("sayHello");
      }

      public sayHello_args getEmptyArgsInstance() {
        return new sayHello_args();
      }

      protected boolean isOneway() {
        return false;
      }

      public sayHello_result getResult(I iface, sayHello_args args)
          throws org.apache.thrift.TException {
        sayHello_result result = new sayHello_result();
        result.success = iface.sayHello(args.zone, args.name);
        return result;
      }
    }

    public static class withError<I extends Iface>
        extends org.apache.thrift.ProcessFunction<I, withError_args> {
      public withError() {
        super("withError");
      }

      public withError_args getEmptyArgsInstance() {
        return new withError_args();
      }

      protected boolean isOneway() {
        return false;
      }

      public withError_result getResult(I iface, withError_args args)
          throws org.apache.thrift.TException {
        withError_result result = new withError_result();
        result.success = iface.withError();
        return result;
      }
    }

    public static class noReturn<I extends Iface>
        extends org.apache.thrift.ProcessFunction<I, noReturn_args> {
      public noReturn() {
        super("noReturn");
      }

      public noReturn_args getEmptyArgsInstance() {
        return new noReturn_args();
      }

      protected boolean isOneway() {
        return false;
      }

      public noReturn_result getResult(I iface, noReturn_args args)
          throws org.apache.thrift.TException {
        noReturn_result result = new noReturn_result();
        iface.noReturn(args.delay);
        return result;
      }
    }

    public static class oneWay<I extends Iface>
        extends org.apache.thrift.ProcessFunction<I, oneWay_args> {
      public oneWay() {
        super("oneWay");
      }

      public oneWay_args getEmptyArgsInstance() {
        return new oneWay_args();
      }

      protected boolean isOneway() {
        return true;
      }

      public org.apache.thrift.TBase getResult(I iface, oneWay_args args)
          throws org.apache.thrift.TException {
        iface.oneWay();
        return null;
      }
    }

    public static class oneWayWithError<I extends Iface>
        extends org.apache.thrift.ProcessFunction<I, oneWayWithError_args> {
      public oneWayWithError() {
        super("oneWayWithError");
      }

      public oneWayWithError_args getEmptyArgsInstance() {
        return new oneWayWithError_args();
      }

      protected boolean isOneway() {
        return true;
      }

      public org.apache.thrift.TBase getResult(I iface, oneWayWithError_args args)
          throws org.apache.thrift.TException {
        iface.oneWayWithError();
        return null;
      }
    }
  }

  public static class AsyncProcessor<I extends AsyncIface>
      extends org.apache.thrift.TBaseAsyncProcessor<I> {
    private static final Logger LOGGER = LoggerFactory.getLogger(AsyncProcessor.class.getName());

    public AsyncProcessor(I iface) {
      super(
          iface,
          getProcessMap(
              new HashMap<
                  String,
                  org.apache.thrift.AsyncProcessFunction<
                      I, ? extends org.apache.thrift.TBase, ?>>()));
    }

    protected AsyncProcessor(
        I iface,
        Map<String, org.apache.thrift.AsyncProcessFunction<I, ? extends org.apache.thrift.TBase, ?>>
            processMap) {
      super(iface, getProcessMap(processMap));
    }

    private static <I extends AsyncIface>
        Map<String, org.apache.thrift.AsyncProcessFunction<I, ? extends org.apache.thrift.TBase, ?>>
            getProcessMap(
                Map<
                        String,
                        org.apache.thrift.AsyncProcessFunction<
                            I, ? extends org.apache.thrift.TBase, ?>>
                    processMap) {
      processMap.put("sayHello", new sayHello());
      processMap.put("withError", new withError());
      processMap.put("noReturn", new noReturn());
      processMap.put("oneWay", new oneWay());
      processMap.put("oneWayWithError", new oneWayWithError());
      return processMap;
    }

    public static class sayHello<I extends AsyncIface>
        extends org.apache.thrift.AsyncProcessFunction<I, sayHello_args, String> {
      public sayHello() {
        super("sayHello");
      }

      public sayHello_args getEmptyArgsInstance() {
        return new sayHello_args();
      }

      public AsyncMethodCallback<String> getResultHandler(
          final AsyncFrameBuffer fb, final int seqid) {
        final org.apache.thrift.AsyncProcessFunction fcall = this;
        return new AsyncMethodCallback<String>() {
          public void onComplete(String o) {
            sayHello_result result = new sayHello_result();
            result.success = o;
            try {
              fcall.sendResponse(fb, result, org.apache.thrift.protocol.TMessageType.REPLY, seqid);
              return;
            } catch (Exception e) {
              LOGGER.error("Exception writing to internal frame buffer", e);
            }
            fb.close();
          }

          public void onError(Exception e) {
            byte msgType = org.apache.thrift.protocol.TMessageType.REPLY;
            org.apache.thrift.TBase msg;
            sayHello_result result = new sayHello_result();
            {
              msgType = org.apache.thrift.protocol.TMessageType.EXCEPTION;
              msg =
                  (org.apache.thrift.TBase)
                      new org.apache.thrift.TApplicationException(
                          org.apache.thrift.TApplicationException.INTERNAL_ERROR, e.getMessage());
            }
            try {
              fcall.sendResponse(fb, msg, msgType, seqid);
              return;
            } catch (Exception ex) {
              LOGGER.error("Exception writing to internal frame buffer", ex);
            }
            fb.close();
          }
        };
      }

      protected boolean isOneway() {
        return false;
      }

      public void start(
          I iface,
          sayHello_args args,
          org.apache.thrift.async.AsyncMethodCallback<String> resultHandler)
          throws TException {
        iface.sayHello(args.zone, args.name, resultHandler);
      }
    }

    public static class withError<I extends AsyncIface>
        extends org.apache.thrift.AsyncProcessFunction<I, withError_args, String> {
      public withError() {
        super("withError");
      }

      public withError_args getEmptyArgsInstance() {
        return new withError_args();
      }

      public AsyncMethodCallback<String> getResultHandler(
          final AsyncFrameBuffer fb, final int seqid) {
        final org.apache.thrift.AsyncProcessFunction fcall = this;
        return new AsyncMethodCallback<String>() {
          public void onComplete(String o) {
            withError_result result = new withError_result();
            result.success = o;
            try {
              fcall.sendResponse(fb, result, org.apache.thrift.protocol.TMessageType.REPLY, seqid);
              return;
            } catch (Exception e) {
              LOGGER.error("Exception writing to internal frame buffer", e);
            }
            fb.close();
          }

          public void onError(Exception e) {
            byte msgType = org.apache.thrift.protocol.TMessageType.REPLY;
            org.apache.thrift.TBase msg;
            withError_result result = new withError_result();
            {
              msgType = org.apache.thrift.protocol.TMessageType.EXCEPTION;
              msg =
                  (org.apache.thrift.TBase)
                      new org.apache.thrift.TApplicationException(
                          org.apache.thrift.TApplicationException.INTERNAL_ERROR, e.getMessage());
            }
            try {
              fcall.sendResponse(fb, msg, msgType, seqid);
              return;
            } catch (Exception ex) {
              LOGGER.error("Exception writing to internal frame buffer", ex);
            }
            fb.close();
          }
        };
      }

      protected boolean isOneway() {
        return false;
      }

      public void start(
          I iface,
          withError_args args,
          org.apache.thrift.async.AsyncMethodCallback<String> resultHandler)
          throws TException {
        iface.withError(resultHandler);
      }
    }

    public static class noReturn<I extends AsyncIface>
        extends org.apache.thrift.AsyncProcessFunction<I, noReturn_args, Void> {
      public noReturn() {
        super("noReturn");
      }

      public noReturn_args getEmptyArgsInstance() {
        return new noReturn_args();
      }

      public AsyncMethodCallback<Void> getResultHandler(
          final AsyncFrameBuffer fb, final int seqid) {
        final org.apache.thrift.AsyncProcessFunction fcall = this;
        return new AsyncMethodCallback<Void>() {
          public void onComplete(Void o) {
            noReturn_result result = new noReturn_result();
            try {
              fcall.sendResponse(fb, result, org.apache.thrift.protocol.TMessageType.REPLY, seqid);
              return;
            } catch (Exception e) {
              LOGGER.error("Exception writing to internal frame buffer", e);
            }
            fb.close();
          }

          public void onError(Exception e) {
            byte msgType = org.apache.thrift.protocol.TMessageType.REPLY;
            org.apache.thrift.TBase msg;
            noReturn_result result = new noReturn_result();
            {
              msgType = org.apache.thrift.protocol.TMessageType.EXCEPTION;
              msg =
                  (org.apache.thrift.TBase)
                      new org.apache.thrift.TApplicationException(
                          org.apache.thrift.TApplicationException.INTERNAL_ERROR, e.getMessage());
            }
            try {
              fcall.sendResponse(fb, msg, msgType, seqid);
              return;
            } catch (Exception ex) {
              LOGGER.error("Exception writing to internal frame buffer", ex);
            }
            fb.close();
          }
        };
      }

      protected boolean isOneway() {
        return false;
      }

      public void start(
          I iface,
          noReturn_args args,
          org.apache.thrift.async.AsyncMethodCallback<Void> resultHandler)
          throws TException {
        iface.noReturn(args.delay, resultHandler);
      }
    }

    public static class oneWay<I extends AsyncIface>
        extends org.apache.thrift.AsyncProcessFunction<I, oneWay_args, Void> {
      public oneWay() {
        super("oneWay");
      }

      public oneWay_args getEmptyArgsInstance() {
        return new oneWay_args();
      }

      public AsyncMethodCallback<Void> getResultHandler(
          final AsyncFrameBuffer fb, final int seqid) {
        final org.apache.thrift.AsyncProcessFunction fcall = this;
        return new AsyncMethodCallback<Void>() {
          public void onComplete(Void o) {}

          public void onError(Exception e) {}
        };
      }

      protected boolean isOneway() {
        return true;
      }

      public void start(
          I iface,
          oneWay_args args,
          org.apache.thrift.async.AsyncMethodCallback<Void> resultHandler)
          throws TException {
        iface.oneWay(resultHandler);
      }
    }

    public static class oneWayWithError<I extends AsyncIface>
        extends org.apache.thrift.AsyncProcessFunction<I, oneWayWithError_args, Void> {
      public oneWayWithError() {
        super("oneWayWithError");
      }

      public oneWayWithError_args getEmptyArgsInstance() {
        return new oneWayWithError_args();
      }

      public AsyncMethodCallback<Void> getResultHandler(
          final AsyncFrameBuffer fb, final int seqid) {
        final org.apache.thrift.AsyncProcessFunction fcall = this;
        return new AsyncMethodCallback<Void>() {
          public void onComplete(Void o) {}

          public void onError(Exception e) {}
        };
      }

      protected boolean isOneway() {
        return true;
      }

      public void start(
          I iface,
          oneWayWithError_args args,
          org.apache.thrift.async.AsyncMethodCallback<Void> resultHandler)
          throws TException {
        iface.oneWayWithError(resultHandler);
      }
    }
  }

  public static class sayHello_args
      implements org.apache.thrift.TBase<sayHello_args, sayHello_args._Fields>,
          java.io.Serializable,
          Cloneable,
          Comparable<sayHello_args> {
    private static final org.apache.thrift.protocol.TStruct STRUCT_DESC =
        new org.apache.thrift.protocol.TStruct("sayHello_args");

    private static final org.apache.thrift.protocol.TField ZONE_FIELD_DESC =
        new org.apache.thrift.protocol.TField(
            "zone", org.apache.thrift.protocol.TType.STRING, (short) 1);
    private static final org.apache.thrift.protocol.TField NAME_FIELD_DESC =
        new org.apache.thrift.protocol.TField(
            "name", org.apache.thrift.protocol.TType.STRING, (short) 2);

    private static final Map<Class<? extends IScheme>, SchemeFactory> schemes =
        new HashMap<Class<? extends IScheme>, SchemeFactory>();

    static {
      schemes.put(StandardScheme.class, new sayHello_argsStandardSchemeFactory());
      schemes.put(TupleScheme.class, new sayHello_argsTupleSchemeFactory());
    }

    public String zone; // required
    public String name; // required

    /**
     * The set of fields this struct contains, along with convenience methods for finding and
     * manipulating them.
     */
    public enum _Fields implements org.apache.thrift.TFieldIdEnum {
      ZONE((short) 1, "zone"),
      NAME((short) 2, "name");

      private static final Map<String, _Fields> byName = new HashMap<String, _Fields>();

      static {
        for (_Fields field : EnumSet.allOf(_Fields.class)) {
          byName.put(field.getFieldName(), field);
        }
      }

      /** Find the _Fields constant that matches fieldId, or null if its not found. */
      public static _Fields findByThriftId(int fieldId) {
        switch (fieldId) {
          case 1: // ZONE
            return ZONE;
          case 2: // NAME
            return NAME;
          default:
            return null;
        }
      }

      /**
       * Find the _Fields constant that matches fieldId, throwing an exception if it is not found.
       */
      public static _Fields findByThriftIdOrThrow(int fieldId) {
        _Fields fields = findByThriftId(fieldId);
        if (fields == null)
          throw new IllegalArgumentException("Field " + fieldId + " doesn't exist!");
        return fields;
      }

      /** Find the _Fields constant that matches name, or null if its not found. */
      public static _Fields findByName(String name) {
        return byName.get(name);
      }

      private final short _thriftId;
      private final String _fieldName;

      _Fields(short thriftId, String fieldName) {
        _thriftId = thriftId;
        _fieldName = fieldName;
      }

      public short getThriftFieldId() {
        return _thriftId;
      }

      public String getFieldName() {
        return _fieldName;
      }
    }

    // isset id assignments
    public static final Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> metaDataMap;

    static {
      Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> tmpMap =
          new EnumMap<_Fields, org.apache.thrift.meta_data.FieldMetaData>(_Fields.class);
      tmpMap.put(
          _Fields.ZONE,
          new org.apache.thrift.meta_data.FieldMetaData(
              "zone",
              org.apache.thrift.TFieldRequirementType.DEFAULT,
              new org.apache.thrift.meta_data.FieldValueMetaData(
                  org.apache.thrift.protocol.TType.STRING)));
      tmpMap.put(
          _Fields.NAME,
          new org.apache.thrift.meta_data.FieldMetaData(
              "name",
              org.apache.thrift.TFieldRequirementType.DEFAULT,
              new org.apache.thrift.meta_data.FieldValueMetaData(
                  org.apache.thrift.protocol.TType.STRING)));
      metaDataMap = Collections.unmodifiableMap(tmpMap);
      org.apache.thrift.meta_data.FieldMetaData.addStructMetaDataMap(
          sayHello_args.class, metaDataMap);
    }

    public sayHello_args() {}

    public sayHello_args(String zone, String name) {
      this();
      this.zone = zone;
      this.name = name;
    }

    /** Performs a deep copy on <i>other</i>. */
    public sayHello_args(sayHello_args other) {
      if (other.isSetZone()) {
        this.zone = other.zone;
      }
      if (other.isSetName()) {
        this.name = other.name;
      }
    }

    public sayHello_args deepCopy() {
      return new sayHello_args(this);
    }

    @Override
    public void clear() {
      this.zone = null;
      this.name = null;
    }

    public String getZone() {
      return this.zone;
    }

    public sayHello_args setZone(String zone) {
      this.zone = zone;
      return this;
    }

    public void unsetZone() {
      this.zone = null;
    }

    /** Returns true if field zone is set (has been assigned a value) and false otherwise */
    public boolean isSetZone() {
      return this.zone != null;
    }

    public void setZoneIsSet(boolean value) {
      if (!value) {
        this.zone = null;
      }
    }

    public String getName() {
      return this.name;
    }

    public sayHello_args setName(String name) {
      this.name = name;
      return this;
    }

    public void unsetName() {
      this.name = null;
    }

    /** Returns true if field name is set (has been assigned a value) and false otherwise */
    public boolean isSetName() {
      return this.name != null;
    }

    public void setNameIsSet(boolean value) {
      if (!value) {
        this.name = null;
      }
    }

    public void setFieldValue(_Fields field, Object value) {
      switch (field) {
        case ZONE:
          if (value == null) {
            unsetZone();
          } else {
            setZone((String) value);
          }
          break;

        case NAME:
          if (value == null) {
            unsetName();
          } else {
            setName((String) value);
          }
          break;
      }
    }

    public Object getFieldValue(_Fields field) {
      switch (field) {
        case ZONE:
          return getZone();

        case NAME:
          return getName();
      }
      throw new IllegalStateException();
    }

    /**
     * Returns true if field corresponding to fieldID is set (has been assigned a value) and false
     * otherwise
     */
    public boolean isSet(_Fields field) {
      if (field == null) {
        throw new IllegalArgumentException();
      }

      switch (field) {
        case ZONE:
          return isSetZone();
        case NAME:
          return isSetName();
      }
      throw new IllegalStateException();
    }

    @Override
    public boolean equals(Object that) {
      if (that == null) return false;
      if (that instanceof sayHello_args) return this.equals((sayHello_args) that);
      return false;
    }

    public boolean equals(sayHello_args that) {
      if (that == null) return false;

      boolean this_present_zone = true && this.isSetZone();
      boolean that_present_zone = true && that.isSetZone();
      if (this_present_zone || that_present_zone) {
        if (!(this_present_zone && that_present_zone)) return false;
        if (!this.zone.equals(that.zone)) return false;
      }

      boolean this_present_name = true && this.isSetName();
      boolean that_present_name = true && that.isSetName();
      if (this_present_name || that_present_name) {
        if (!(this_present_name && that_present_name)) return false;
        if (!this.name.equals(that.name)) return false;
      }

      return true;
    }

    @Override
    public int hashCode() {
      return 0;
    }

    @Override
    public int compareTo(sayHello_args other) {
      if (!getClass().equals(other.getClass())) {
        return getClass().getName().compareTo(other.getClass().getName());
      }

      int lastComparison = 0;

      lastComparison = Boolean.valueOf(isSetZone()).compareTo(other.isSetZone());
      if (lastComparison != 0) {
        return lastComparison;
      }
      if (isSetZone()) {
        lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.zone, other.zone);
        if (lastComparison != 0) {
          return lastComparison;
        }
      }
      lastComparison = Boolean.valueOf(isSetName()).compareTo(other.isSetName());
      if (lastComparison != 0) {
        return lastComparison;
      }
      if (isSetName()) {
        lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.name, other.name);
        if (lastComparison != 0) {
          return lastComparison;
        }
      }
      return 0;
    }

    public _Fields fieldForId(int fieldId) {
      return _Fields.findByThriftId(fieldId);
    }

    public void read(org.apache.thrift.protocol.TProtocol iprot)
        throws org.apache.thrift.TException {
      schemes.get(iprot.getScheme()).getScheme().read(iprot, this);
    }

    public void write(org.apache.thrift.protocol.TProtocol oprot)
        throws org.apache.thrift.TException {
      schemes.get(oprot.getScheme()).getScheme().write(oprot, this);
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder("sayHello_args(");
      boolean first = true;

      sb.append("zone:");
      if (this.zone == null) {
        sb.append("null");
      } else {
        sb.append(this.zone);
      }
      first = false;
      if (!first) sb.append(", ");
      sb.append("name:");
      if (this.name == null) {
        sb.append("null");
      } else {
        sb.append(this.name);
      }
      first = false;
      sb.append(")");
      return sb.toString();
    }

    public void validate() throws org.apache.thrift.TException {
      // check for required fields
      // check for sub-struct validity
    }

    private void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException {
      try {
        write(
            new org.apache.thrift.protocol.TCompactProtocol(
                new org.apache.thrift.transport.TIOStreamTransport(out)));
      } catch (org.apache.thrift.TException te) {
        throw new java.io.IOException(te);
      }
    }

    private void readObject(java.io.ObjectInputStream in)
        throws java.io.IOException, ClassNotFoundException {
      try {
        read(
            new org.apache.thrift.protocol.TCompactProtocol(
                new org.apache.thrift.transport.TIOStreamTransport(in)));
      } catch (org.apache.thrift.TException te) {
        throw new java.io.IOException(te);
      }
    }

    private static class sayHello_argsStandardSchemeFactory implements SchemeFactory {
      public sayHello_argsStandardScheme getScheme() {
        return new sayHello_argsStandardScheme();
      }
    }

    private static class sayHello_argsStandardScheme extends StandardScheme<sayHello_args> {

      public void read(org.apache.thrift.protocol.TProtocol iprot, sayHello_args struct)
          throws org.apache.thrift.TException {
        org.apache.thrift.protocol.TField schemeField;
        iprot.readStructBegin();
        while (true) {
          schemeField = iprot.readFieldBegin();
          if (schemeField.type == org.apache.thrift.protocol.TType.STOP) {
            break;
          }
          switch (schemeField.id) {
            case 1: // ZONE
              if (schemeField.type == org.apache.thrift.protocol.TType.STRING) {
                struct.zone = iprot.readString();
                struct.setZoneIsSet(true);
              } else {
                org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
              }
              break;
            case 2: // NAME
              if (schemeField.type == org.apache.thrift.protocol.TType.STRING) {
                struct.name = iprot.readString();
                struct.setNameIsSet(true);
              } else {
                org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
              }
              break;
            default:
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
          }
          iprot.readFieldEnd();
        }
        iprot.readStructEnd();

        // check for required fields of primitive type, which can't be checked in the validate
        // method
        struct.validate();
      }

      public void write(org.apache.thrift.protocol.TProtocol oprot, sayHello_args struct)
          throws org.apache.thrift.TException {
        struct.validate();

        oprot.writeStructBegin(STRUCT_DESC);
        if (struct.zone != null) {
          oprot.writeFieldBegin(ZONE_FIELD_DESC);
          oprot.writeString(struct.zone);
          oprot.writeFieldEnd();
        }
        if (struct.name != null) {
          oprot.writeFieldBegin(NAME_FIELD_DESC);
          oprot.writeString(struct.name);
          oprot.writeFieldEnd();
        }
        oprot.writeFieldStop();
        oprot.writeStructEnd();
      }
    }

    private static class sayHello_argsTupleSchemeFactory implements SchemeFactory {
      public sayHello_argsTupleScheme getScheme() {
        return new sayHello_argsTupleScheme();
      }
    }

    private static class sayHello_argsTupleScheme extends TupleScheme<sayHello_args> {

      @Override
      public void write(org.apache.thrift.protocol.TProtocol prot, sayHello_args struct)
          throws org.apache.thrift.TException {
        TTupleProtocol oprot = (TTupleProtocol) prot;
        BitSet optionals = new BitSet();
        if (struct.isSetZone()) {
          optionals.set(0);
        }
        if (struct.isSetName()) {
          optionals.set(1);
        }
        oprot.writeBitSet(optionals, 2);
        if (struct.isSetZone()) {
          oprot.writeString(struct.zone);
        }
        if (struct.isSetName()) {
          oprot.writeString(struct.name);
        }
      }

      @Override
      public void read(org.apache.thrift.protocol.TProtocol prot, sayHello_args struct)
          throws org.apache.thrift.TException {
        TTupleProtocol iprot = (TTupleProtocol) prot;
        BitSet incoming = iprot.readBitSet(2);
        if (incoming.get(0)) {
          struct.zone = iprot.readString();
          struct.setZoneIsSet(true);
        }
        if (incoming.get(1)) {
          struct.name = iprot.readString();
          struct.setNameIsSet(true);
        }
      }
    }
  }

  public static class sayHello_result
      implements org.apache.thrift.TBase<sayHello_result, sayHello_result._Fields>,
          java.io.Serializable,
          Cloneable,
          Comparable<sayHello_result> {
    private static final org.apache.thrift.protocol.TStruct STRUCT_DESC =
        new org.apache.thrift.protocol.TStruct("sayHello_result");

    private static final org.apache.thrift.protocol.TField SUCCESS_FIELD_DESC =
        new org.apache.thrift.protocol.TField(
            "success", org.apache.thrift.protocol.TType.STRING, (short) 0);

    private static final Map<Class<? extends IScheme>, SchemeFactory> schemes =
        new HashMap<Class<? extends IScheme>, SchemeFactory>();

    static {
      schemes.put(StandardScheme.class, new sayHello_resultStandardSchemeFactory());
      schemes.put(TupleScheme.class, new sayHello_resultTupleSchemeFactory());
    }

    public String success; // required

    /**
     * The set of fields this struct contains, along with convenience methods for finding and
     * manipulating them.
     */
    public enum _Fields implements org.apache.thrift.TFieldIdEnum {
      SUCCESS((short) 0, "success");

      private static final Map<String, _Fields> byName = new HashMap<String, _Fields>();

      static {
        for (_Fields field : EnumSet.allOf(_Fields.class)) {
          byName.put(field.getFieldName(), field);
        }
      }

      /** Find the _Fields constant that matches fieldId, or null if its not found. */
      public static _Fields findByThriftId(int fieldId) {
        switch (fieldId) {
          case 0: // SUCCESS
            return SUCCESS;
          default:
            return null;
        }
      }

      /**
       * Find the _Fields constant that matches fieldId, throwing an exception if it is not found.
       */
      public static _Fields findByThriftIdOrThrow(int fieldId) {
        _Fields fields = findByThriftId(fieldId);
        if (fields == null)
          throw new IllegalArgumentException("Field " + fieldId + " doesn't exist!");
        return fields;
      }

      /** Find the _Fields constant that matches name, or null if its not found. */
      public static _Fields findByName(String name) {
        return byName.get(name);
      }

      private final short _thriftId;
      private final String _fieldName;

      _Fields(short thriftId, String fieldName) {
        _thriftId = thriftId;
        _fieldName = fieldName;
      }

      public short getThriftFieldId() {
        return _thriftId;
      }

      public String getFieldName() {
        return _fieldName;
      }
    }

    // isset id assignments
    public static final Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> metaDataMap;

    static {
      Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> tmpMap =
          new EnumMap<_Fields, org.apache.thrift.meta_data.FieldMetaData>(_Fields.class);
      tmpMap.put(
          _Fields.SUCCESS,
          new org.apache.thrift.meta_data.FieldMetaData(
              "success",
              org.apache.thrift.TFieldRequirementType.DEFAULT,
              new org.apache.thrift.meta_data.FieldValueMetaData(
                  org.apache.thrift.protocol.TType.STRING)));
      metaDataMap = Collections.unmodifiableMap(tmpMap);
      org.apache.thrift.meta_data.FieldMetaData.addStructMetaDataMap(
          sayHello_result.class, metaDataMap);
    }

    public sayHello_result() {}

    public sayHello_result(String success) {
      this();
      this.success = success;
    }

    /** Performs a deep copy on <i>other</i>. */
    public sayHello_result(sayHello_result other) {
      if (other.isSetSuccess()) {
        this.success = other.success;
      }
    }

    public sayHello_result deepCopy() {
      return new sayHello_result(this);
    }

    @Override
    public void clear() {
      this.success = null;
    }

    public String getSuccess() {
      return this.success;
    }

    public sayHello_result setSuccess(String success) {
      this.success = success;
      return this;
    }

    public void unsetSuccess() {
      this.success = null;
    }

    /** Returns true if field success is set (has been assigned a value) and false otherwise */
    public boolean isSetSuccess() {
      return this.success != null;
    }

    public void setSuccessIsSet(boolean value) {
      if (!value) {
        this.success = null;
      }
    }

    public void setFieldValue(_Fields field, Object value) {
      switch (field) {
        case SUCCESS:
          if (value == null) {
            unsetSuccess();
          } else {
            setSuccess((String) value);
          }
          break;
      }
    }

    public Object getFieldValue(_Fields field) {
      switch (field) {
        case SUCCESS:
          return getSuccess();
      }
      throw new IllegalStateException();
    }

    /**
     * Returns true if field corresponding to fieldID is set (has been assigned a value) and false
     * otherwise
     */
    public boolean isSet(_Fields field) {
      if (field == null) {
        throw new IllegalArgumentException();
      }

      switch (field) {
        case SUCCESS:
          return isSetSuccess();
      }
      throw new IllegalStateException();
    }

    @Override
    public boolean equals(Object that) {
      if (that == null) return false;
      if (that instanceof sayHello_result) return this.equals((sayHello_result) that);
      return false;
    }

    public boolean equals(sayHello_result that) {
      if (that == null) return false;

      boolean this_present_success = true && this.isSetSuccess();
      boolean that_present_success = true && that.isSetSuccess();
      if (this_present_success || that_present_success) {
        if (!(this_present_success && that_present_success)) return false;
        if (!this.success.equals(that.success)) return false;
      }

      return true;
    }

    @Override
    public int hashCode() {
      return 0;
    }

    @Override
    public int compareTo(sayHello_result other) {
      if (!getClass().equals(other.getClass())) {
        return getClass().getName().compareTo(other.getClass().getName());
      }

      int lastComparison = 0;

      lastComparison = Boolean.valueOf(isSetSuccess()).compareTo(other.isSetSuccess());
      if (lastComparison != 0) {
        return lastComparison;
      }
      if (isSetSuccess()) {
        lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.success, other.success);
        if (lastComparison != 0) {
          return lastComparison;
        }
      }
      return 0;
    }

    public _Fields fieldForId(int fieldId) {
      return _Fields.findByThriftId(fieldId);
    }

    public void read(org.apache.thrift.protocol.TProtocol iprot)
        throws org.apache.thrift.TException {
      schemes.get(iprot.getScheme()).getScheme().read(iprot, this);
    }

    public void write(org.apache.thrift.protocol.TProtocol oprot)
        throws org.apache.thrift.TException {
      schemes.get(oprot.getScheme()).getScheme().write(oprot, this);
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder("sayHello_result(");
      boolean first = true;

      sb.append("success:");
      if (this.success == null) {
        sb.append("null");
      } else {
        sb.append(this.success);
      }
      first = false;
      sb.append(")");
      return sb.toString();
    }

    public void validate() throws org.apache.thrift.TException {
      // check for required fields
      // check for sub-struct validity
    }

    private void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException {
      try {
        write(
            new org.apache.thrift.protocol.TCompactProtocol(
                new org.apache.thrift.transport.TIOStreamTransport(out)));
      } catch (org.apache.thrift.TException te) {
        throw new java.io.IOException(te);
      }
    }

    private void readObject(java.io.ObjectInputStream in)
        throws java.io.IOException, ClassNotFoundException {
      try {
        read(
            new org.apache.thrift.protocol.TCompactProtocol(
                new org.apache.thrift.transport.TIOStreamTransport(in)));
      } catch (org.apache.thrift.TException te) {
        throw new java.io.IOException(te);
      }
    }

    private static class sayHello_resultStandardSchemeFactory implements SchemeFactory {
      public sayHello_resultStandardScheme getScheme() {
        return new sayHello_resultStandardScheme();
      }
    }

    private static class sayHello_resultStandardScheme extends StandardScheme<sayHello_result> {

      public void read(org.apache.thrift.protocol.TProtocol iprot, sayHello_result struct)
          throws org.apache.thrift.TException {
        org.apache.thrift.protocol.TField schemeField;
        iprot.readStructBegin();
        while (true) {
          schemeField = iprot.readFieldBegin();
          if (schemeField.type == org.apache.thrift.protocol.TType.STOP) {
            break;
          }
          switch (schemeField.id) {
            case 0: // SUCCESS
              if (schemeField.type == org.apache.thrift.protocol.TType.STRING) {
                struct.success = iprot.readString();
                struct.setSuccessIsSet(true);
              } else {
                org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
              }
              break;
            default:
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
          }
          iprot.readFieldEnd();
        }
        iprot.readStructEnd();

        // check for required fields of primitive type, which can't be checked in the validate
        // method
        struct.validate();
      }

      public void write(org.apache.thrift.protocol.TProtocol oprot, sayHello_result struct)
          throws org.apache.thrift.TException {
        struct.validate();

        oprot.writeStructBegin(STRUCT_DESC);
        if (struct.success != null) {
          oprot.writeFieldBegin(SUCCESS_FIELD_DESC);
          oprot.writeString(struct.success);
          oprot.writeFieldEnd();
        }
        oprot.writeFieldStop();
        oprot.writeStructEnd();
      }
    }

    private static class sayHello_resultTupleSchemeFactory implements SchemeFactory {
      public sayHello_resultTupleScheme getScheme() {
        return new sayHello_resultTupleScheme();
      }
    }

    private static class sayHello_resultTupleScheme extends TupleScheme<sayHello_result> {

      @Override
      public void write(org.apache.thrift.protocol.TProtocol prot, sayHello_result struct)
          throws org.apache.thrift.TException {
        TTupleProtocol oprot = (TTupleProtocol) prot;
        BitSet optionals = new BitSet();
        if (struct.isSetSuccess()) {
          optionals.set(0);
        }
        oprot.writeBitSet(optionals, 1);
        if (struct.isSetSuccess()) {
          oprot.writeString(struct.success);
        }
      }

      @Override
      public void read(org.apache.thrift.protocol.TProtocol prot, sayHello_result struct)
          throws org.apache.thrift.TException {
        TTupleProtocol iprot = (TTupleProtocol) prot;
        BitSet incoming = iprot.readBitSet(1);
        if (incoming.get(0)) {
          struct.success = iprot.readString();
          struct.setSuccessIsSet(true);
        }
      }
    }
  }

  public static class withError_args
      implements org.apache.thrift.TBase<withError_args, withError_args._Fields>,
          java.io.Serializable,
          Cloneable,
          Comparable<withError_args> {
    private static final org.apache.thrift.protocol.TStruct STRUCT_DESC =
        new org.apache.thrift.protocol.TStruct("withError_args");

    private static final Map<Class<? extends IScheme>, SchemeFactory> schemes =
        new HashMap<Class<? extends IScheme>, SchemeFactory>();

    static {
      schemes.put(StandardScheme.class, new withError_argsStandardSchemeFactory());
      schemes.put(TupleScheme.class, new withError_argsTupleSchemeFactory());
    }

    /**
     * The set of fields this struct contains, along with convenience methods for finding and
     * manipulating them.
     */
    public enum _Fields implements org.apache.thrift.TFieldIdEnum {
      ;

      private static final Map<String, _Fields> byName = new HashMap<String, _Fields>();

      static {
        for (_Fields field : EnumSet.allOf(_Fields.class)) {
          byName.put(field.getFieldName(), field);
        }
      }

      /** Find the _Fields constant that matches fieldId, or null if its not found. */
      public static _Fields findByThriftId(int fieldId) {
        switch (fieldId) {
          default:
            return null;
        }
      }

      /**
       * Find the _Fields constant that matches fieldId, throwing an exception if it is not found.
       */
      public static _Fields findByThriftIdOrThrow(int fieldId) {
        _Fields fields = findByThriftId(fieldId);
        if (fields == null)
          throw new IllegalArgumentException("Field " + fieldId + " doesn't exist!");
        return fields;
      }

      /** Find the _Fields constant that matches name, or null if its not found. */
      public static _Fields findByName(String name) {
        return byName.get(name);
      }

      private final short _thriftId;
      private final String _fieldName;

      _Fields(short thriftId, String fieldName) {
        _thriftId = thriftId;
        _fieldName = fieldName;
      }

      public short getThriftFieldId() {
        return _thriftId;
      }

      public String getFieldName() {
        return _fieldName;
      }
    }

    public static final Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> metaDataMap;

    static {
      Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> tmpMap =
          new EnumMap<_Fields, org.apache.thrift.meta_data.FieldMetaData>(_Fields.class);
      metaDataMap = Collections.unmodifiableMap(tmpMap);
      org.apache.thrift.meta_data.FieldMetaData.addStructMetaDataMap(
          withError_args.class, metaDataMap);
    }

    public withError_args() {}

    /** Performs a deep copy on <i>other</i>. */
    public withError_args(withError_args other) {}

    public withError_args deepCopy() {
      return new withError_args(this);
    }

    @Override
    public void clear() {}

    public void setFieldValue(_Fields field, Object value) {
      switch (field) {
      }
    }

    public Object getFieldValue(_Fields field) {
      switch (field) {
      }
      throw new IllegalStateException();
    }

    /**
     * Returns true if field corresponding to fieldID is set (has been assigned a value) and false
     * otherwise
     */
    public boolean isSet(_Fields field) {
      if (field == null) {
        throw new IllegalArgumentException();
      }

      switch (field) {
      }
      throw new IllegalStateException();
    }

    @Override
    public boolean equals(Object that) {
      if (that == null) return false;
      if (that instanceof withError_args) return this.equals((withError_args) that);
      return false;
    }

    public boolean equals(withError_args that) {
      if (that == null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return 0;
    }

    @Override
    public int compareTo(withError_args other) {
      if (!getClass().equals(other.getClass())) {
        return getClass().getName().compareTo(other.getClass().getName());
      }

      int lastComparison = 0;

      return 0;
    }

    public _Fields fieldForId(int fieldId) {
      return _Fields.findByThriftId(fieldId);
    }

    public void read(org.apache.thrift.protocol.TProtocol iprot)
        throws org.apache.thrift.TException {
      schemes.get(iprot.getScheme()).getScheme().read(iprot, this);
    }

    public void write(org.apache.thrift.protocol.TProtocol oprot)
        throws org.apache.thrift.TException {
      schemes.get(oprot.getScheme()).getScheme().write(oprot, this);
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder("withError_args(");
      boolean first = true;

      sb.append(")");
      return sb.toString();
    }

    public void validate() throws org.apache.thrift.TException {
      // check for required fields
      // check for sub-struct validity
    }

    private void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException {
      try {
        write(
            new org.apache.thrift.protocol.TCompactProtocol(
                new org.apache.thrift.transport.TIOStreamTransport(out)));
      } catch (org.apache.thrift.TException te) {
        throw new java.io.IOException(te);
      }
    }

    private void readObject(java.io.ObjectInputStream in)
        throws java.io.IOException, ClassNotFoundException {
      try {
        read(
            new org.apache.thrift.protocol.TCompactProtocol(
                new org.apache.thrift.transport.TIOStreamTransport(in)));
      } catch (org.apache.thrift.TException te) {
        throw new java.io.IOException(te);
      }
    }

    private static class withError_argsStandardSchemeFactory implements SchemeFactory {
      public withError_argsStandardScheme getScheme() {
        return new withError_argsStandardScheme();
      }
    }

    private static class withError_argsStandardScheme extends StandardScheme<withError_args> {

      public void read(org.apache.thrift.protocol.TProtocol iprot, withError_args struct)
          throws org.apache.thrift.TException {
        org.apache.thrift.protocol.TField schemeField;
        iprot.readStructBegin();
        while (true) {
          schemeField = iprot.readFieldBegin();
          if (schemeField.type == org.apache.thrift.protocol.TType.STOP) {
            break;
          }
          switch (schemeField.id) {
            default:
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
          }
          iprot.readFieldEnd();
        }
        iprot.readStructEnd();

        // check for required fields of primitive type, which can't be checked in the validate
        // method
        struct.validate();
      }

      public void write(org.apache.thrift.protocol.TProtocol oprot, withError_args struct)
          throws org.apache.thrift.TException {
        struct.validate();

        oprot.writeStructBegin(STRUCT_DESC);
        oprot.writeFieldStop();
        oprot.writeStructEnd();
      }
    }

    private static class withError_argsTupleSchemeFactory implements SchemeFactory {
      public withError_argsTupleScheme getScheme() {
        return new withError_argsTupleScheme();
      }
    }

    private static class withError_argsTupleScheme extends TupleScheme<withError_args> {

      @Override
      public void write(org.apache.thrift.protocol.TProtocol prot, withError_args struct)
          throws org.apache.thrift.TException {
        TTupleProtocol oprot = (TTupleProtocol) prot;
      }

      @Override
      public void read(org.apache.thrift.protocol.TProtocol prot, withError_args struct)
          throws org.apache.thrift.TException {
        TTupleProtocol iprot = (TTupleProtocol) prot;
      }
    }
  }

  public static class withError_result
      implements org.apache.thrift.TBase<withError_result, withError_result._Fields>,
          java.io.Serializable,
          Cloneable,
          Comparable<withError_result> {
    private static final org.apache.thrift.protocol.TStruct STRUCT_DESC =
        new org.apache.thrift.protocol.TStruct("withError_result");

    private static final org.apache.thrift.protocol.TField SUCCESS_FIELD_DESC =
        new org.apache.thrift.protocol.TField(
            "success", org.apache.thrift.protocol.TType.STRING, (short) 0);

    private static final Map<Class<? extends IScheme>, SchemeFactory> schemes =
        new HashMap<Class<? extends IScheme>, SchemeFactory>();

    static {
      schemes.put(StandardScheme.class, new withError_resultStandardSchemeFactory());
      schemes.put(TupleScheme.class, new withError_resultTupleSchemeFactory());
    }

    public String success; // required

    /**
     * The set of fields this struct contains, along with convenience methods for finding and
     * manipulating them.
     */
    public enum _Fields implements org.apache.thrift.TFieldIdEnum {
      SUCCESS((short) 0, "success");

      private static final Map<String, _Fields> byName = new HashMap<String, _Fields>();

      static {
        for (_Fields field : EnumSet.allOf(_Fields.class)) {
          byName.put(field.getFieldName(), field);
        }
      }

      /** Find the _Fields constant that matches fieldId, or null if its not found. */
      public static _Fields findByThriftId(int fieldId) {
        switch (fieldId) {
          case 0: // SUCCESS
            return SUCCESS;
          default:
            return null;
        }
      }

      /**
       * Find the _Fields constant that matches fieldId, throwing an exception if it is not found.
       */
      public static _Fields findByThriftIdOrThrow(int fieldId) {
        _Fields fields = findByThriftId(fieldId);
        if (fields == null)
          throw new IllegalArgumentException("Field " + fieldId + " doesn't exist!");
        return fields;
      }

      /** Find the _Fields constant that matches name, or null if its not found. */
      public static _Fields findByName(String name) {
        return byName.get(name);
      }

      private final short _thriftId;
      private final String _fieldName;

      _Fields(short thriftId, String fieldName) {
        _thriftId = thriftId;
        _fieldName = fieldName;
      }

      public short getThriftFieldId() {
        return _thriftId;
      }

      public String getFieldName() {
        return _fieldName;
      }
    }

    // isset id assignments
    public static final Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> metaDataMap;

    static {
      Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> tmpMap =
          new EnumMap<_Fields, org.apache.thrift.meta_data.FieldMetaData>(_Fields.class);
      tmpMap.put(
          _Fields.SUCCESS,
          new org.apache.thrift.meta_data.FieldMetaData(
              "success",
              org.apache.thrift.TFieldRequirementType.DEFAULT,
              new org.apache.thrift.meta_data.FieldValueMetaData(
                  org.apache.thrift.protocol.TType.STRING)));
      metaDataMap = Collections.unmodifiableMap(tmpMap);
      org.apache.thrift.meta_data.FieldMetaData.addStructMetaDataMap(
          withError_result.class, metaDataMap);
    }

    public withError_result() {}

    public withError_result(String success) {
      this();
      this.success = success;
    }

    /** Performs a deep copy on <i>other</i>. */
    public withError_result(withError_result other) {
      if (other.isSetSuccess()) {
        this.success = other.success;
      }
    }

    public withError_result deepCopy() {
      return new withError_result(this);
    }

    @Override
    public void clear() {
      this.success = null;
    }

    public String getSuccess() {
      return this.success;
    }

    public withError_result setSuccess(String success) {
      this.success = success;
      return this;
    }

    public void unsetSuccess() {
      this.success = null;
    }

    /** Returns true if field success is set (has been assigned a value) and false otherwise */
    public boolean isSetSuccess() {
      return this.success != null;
    }

    public void setSuccessIsSet(boolean value) {
      if (!value) {
        this.success = null;
      }
    }

    public void setFieldValue(_Fields field, Object value) {
      switch (field) {
        case SUCCESS:
          if (value == null) {
            unsetSuccess();
          } else {
            setSuccess((String) value);
          }
          break;
      }
    }

    public Object getFieldValue(_Fields field) {
      switch (field) {
        case SUCCESS:
          return getSuccess();
      }
      throw new IllegalStateException();
    }

    /**
     * Returns true if field corresponding to fieldID is set (has been assigned a value) and false
     * otherwise
     */
    public boolean isSet(_Fields field) {
      if (field == null) {
        throw new IllegalArgumentException();
      }

      switch (field) {
        case SUCCESS:
          return isSetSuccess();
      }
      throw new IllegalStateException();
    }

    @Override
    public boolean equals(Object that) {
      if (that == null) return false;
      if (that instanceof withError_result) return this.equals((withError_result) that);
      return false;
    }

    public boolean equals(withError_result that) {
      if (that == null) return false;

      boolean this_present_success = true && this.isSetSuccess();
      boolean that_present_success = true && that.isSetSuccess();
      if (this_present_success || that_present_success) {
        if (!(this_present_success && that_present_success)) return false;
        if (!this.success.equals(that.success)) return false;
      }

      return true;
    }

    @Override
    public int hashCode() {
      return 0;
    }

    @Override
    public int compareTo(withError_result other) {
      if (!getClass().equals(other.getClass())) {
        return getClass().getName().compareTo(other.getClass().getName());
      }

      int lastComparison = 0;

      lastComparison = Boolean.valueOf(isSetSuccess()).compareTo(other.isSetSuccess());
      if (lastComparison != 0) {
        return lastComparison;
      }
      if (isSetSuccess()) {
        lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.success, other.success);
        if (lastComparison != 0) {
          return lastComparison;
        }
      }
      return 0;
    }

    public _Fields fieldForId(int fieldId) {
      return _Fields.findByThriftId(fieldId);
    }

    public void read(org.apache.thrift.protocol.TProtocol iprot)
        throws org.apache.thrift.TException {
      schemes.get(iprot.getScheme()).getScheme().read(iprot, this);
    }

    public void write(org.apache.thrift.protocol.TProtocol oprot)
        throws org.apache.thrift.TException {
      schemes.get(oprot.getScheme()).getScheme().write(oprot, this);
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder("withError_result(");
      boolean first = true;

      sb.append("success:");
      if (this.success == null) {
        sb.append("null");
      } else {
        sb.append(this.success);
      }
      first = false;
      sb.append(")");
      return sb.toString();
    }

    public void validate() throws org.apache.thrift.TException {
      // check for required fields
      // check for sub-struct validity
    }

    private void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException {
      try {
        write(
            new org.apache.thrift.protocol.TCompactProtocol(
                new org.apache.thrift.transport.TIOStreamTransport(out)));
      } catch (org.apache.thrift.TException te) {
        throw new java.io.IOException(te);
      }
    }

    private void readObject(java.io.ObjectInputStream in)
        throws java.io.IOException, ClassNotFoundException {
      try {
        read(
            new org.apache.thrift.protocol.TCompactProtocol(
                new org.apache.thrift.transport.TIOStreamTransport(in)));
      } catch (org.apache.thrift.TException te) {
        throw new java.io.IOException(te);
      }
    }

    private static class withError_resultStandardSchemeFactory implements SchemeFactory {
      public withError_resultStandardScheme getScheme() {
        return new withError_resultStandardScheme();
      }
    }

    private static class withError_resultStandardScheme extends StandardScheme<withError_result> {

      public void read(org.apache.thrift.protocol.TProtocol iprot, withError_result struct)
          throws org.apache.thrift.TException {
        org.apache.thrift.protocol.TField schemeField;
        iprot.readStructBegin();
        while (true) {
          schemeField = iprot.readFieldBegin();
          if (schemeField.type == org.apache.thrift.protocol.TType.STOP) {
            break;
          }
          switch (schemeField.id) {
            case 0: // SUCCESS
              if (schemeField.type == org.apache.thrift.protocol.TType.STRING) {
                struct.success = iprot.readString();
                struct.setSuccessIsSet(true);
              } else {
                org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
              }
              break;
            default:
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
          }
          iprot.readFieldEnd();
        }
        iprot.readStructEnd();

        // check for required fields of primitive type, which can't be checked in the validate
        // method
        struct.validate();
      }

      public void write(org.apache.thrift.protocol.TProtocol oprot, withError_result struct)
          throws org.apache.thrift.TException {
        struct.validate();

        oprot.writeStructBegin(STRUCT_DESC);
        if (struct.success != null) {
          oprot.writeFieldBegin(SUCCESS_FIELD_DESC);
          oprot.writeString(struct.success);
          oprot.writeFieldEnd();
        }
        oprot.writeFieldStop();
        oprot.writeStructEnd();
      }
    }

    private static class withError_resultTupleSchemeFactory implements SchemeFactory {
      public withError_resultTupleScheme getScheme() {
        return new withError_resultTupleScheme();
      }
    }

    private static class withError_resultTupleScheme extends TupleScheme<withError_result> {

      @Override
      public void write(org.apache.thrift.protocol.TProtocol prot, withError_result struct)
          throws org.apache.thrift.TException {
        TTupleProtocol oprot = (TTupleProtocol) prot;
        BitSet optionals = new BitSet();
        if (struct.isSetSuccess()) {
          optionals.set(0);
        }
        oprot.writeBitSet(optionals, 1);
        if (struct.isSetSuccess()) {
          oprot.writeString(struct.success);
        }
      }

      @Override
      public void read(org.apache.thrift.protocol.TProtocol prot, withError_result struct)
          throws org.apache.thrift.TException {
        TTupleProtocol iprot = (TTupleProtocol) prot;
        BitSet incoming = iprot.readBitSet(1);
        if (incoming.get(0)) {
          struct.success = iprot.readString();
          struct.setSuccessIsSet(true);
        }
      }
    }
  }

  public static class noReturn_args
      implements org.apache.thrift.TBase<noReturn_args, noReturn_args._Fields>,
          java.io.Serializable,
          Cloneable,
          Comparable<noReturn_args> {
    private static final org.apache.thrift.protocol.TStruct STRUCT_DESC =
        new org.apache.thrift.protocol.TStruct("noReturn_args");

    private static final org.apache.thrift.protocol.TField DELAY_FIELD_DESC =
        new org.apache.thrift.protocol.TField(
            "delay", org.apache.thrift.protocol.TType.I32, (short) 1);

    private static final Map<Class<? extends IScheme>, SchemeFactory> schemes =
        new HashMap<Class<? extends IScheme>, SchemeFactory>();

    static {
      schemes.put(StandardScheme.class, new noReturn_argsStandardSchemeFactory());
      schemes.put(TupleScheme.class, new noReturn_argsTupleSchemeFactory());
    }

    public int delay; // required

    /**
     * The set of fields this struct contains, along with convenience methods for finding and
     * manipulating them.
     */
    public enum _Fields implements org.apache.thrift.TFieldIdEnum {
      DELAY((short) 1, "delay");

      private static final Map<String, _Fields> byName = new HashMap<String, _Fields>();

      static {
        for (_Fields field : EnumSet.allOf(_Fields.class)) {
          byName.put(field.getFieldName(), field);
        }
      }

      /** Find the _Fields constant that matches fieldId, or null if its not found. */
      public static _Fields findByThriftId(int fieldId) {
        switch (fieldId) {
          case 1: // DELAY
            return DELAY;
          default:
            return null;
        }
      }

      /**
       * Find the _Fields constant that matches fieldId, throwing an exception if it is not found.
       */
      public static _Fields findByThriftIdOrThrow(int fieldId) {
        _Fields fields = findByThriftId(fieldId);
        if (fields == null)
          throw new IllegalArgumentException("Field " + fieldId + " doesn't exist!");
        return fields;
      }

      /** Find the _Fields constant that matches name, or null if its not found. */
      public static _Fields findByName(String name) {
        return byName.get(name);
      }

      private final short _thriftId;
      private final String _fieldName;

      _Fields(short thriftId, String fieldName) {
        _thriftId = thriftId;
        _fieldName = fieldName;
      }

      public short getThriftFieldId() {
        return _thriftId;
      }

      public String getFieldName() {
        return _fieldName;
      }
    }

    // isset id assignments
    private static final int __DELAY_ISSET_ID = 0;
    private byte __isset_bitfield = 0;
    public static final Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> metaDataMap;

    static {
      Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> tmpMap =
          new EnumMap<_Fields, org.apache.thrift.meta_data.FieldMetaData>(_Fields.class);
      tmpMap.put(
          _Fields.DELAY,
          new org.apache.thrift.meta_data.FieldMetaData(
              "delay",
              org.apache.thrift.TFieldRequirementType.DEFAULT,
              new org.apache.thrift.meta_data.FieldValueMetaData(
                  org.apache.thrift.protocol.TType.I32)));
      metaDataMap = Collections.unmodifiableMap(tmpMap);
      org.apache.thrift.meta_data.FieldMetaData.addStructMetaDataMap(
          noReturn_args.class, metaDataMap);
    }

    public noReturn_args() {}

    public noReturn_args(int delay) {
      this();
      this.delay = delay;
      setDelayIsSet(true);
    }

    /** Performs a deep copy on <i>other</i>. */
    public noReturn_args(noReturn_args other) {
      __isset_bitfield = other.__isset_bitfield;
      this.delay = other.delay;
    }

    public noReturn_args deepCopy() {
      return new noReturn_args(this);
    }

    @Override
    public void clear() {
      setDelayIsSet(false);
      this.delay = 0;
    }

    public int getDelay() {
      return this.delay;
    }

    public noReturn_args setDelay(int delay) {
      this.delay = delay;
      setDelayIsSet(true);
      return this;
    }

    public void unsetDelay() {
      __isset_bitfield = EncodingUtils.clearBit(__isset_bitfield, __DELAY_ISSET_ID);
    }

    /** Returns true if field delay is set (has been assigned a value) and false otherwise */
    public boolean isSetDelay() {
      return EncodingUtils.testBit(__isset_bitfield, __DELAY_ISSET_ID);
    }

    public void setDelayIsSet(boolean value) {
      __isset_bitfield = EncodingUtils.setBit(__isset_bitfield, __DELAY_ISSET_ID, value);
    }

    public void setFieldValue(_Fields field, Object value) {
      switch (field) {
        case DELAY:
          if (value == null) {
            unsetDelay();
          } else {
            setDelay((Integer) value);
          }
          break;
      }
    }

    public Object getFieldValue(_Fields field) {
      switch (field) {
        case DELAY:
          return Integer.valueOf(getDelay());
      }
      throw new IllegalStateException();
    }

    /**
     * Returns true if field corresponding to fieldID is set (has been assigned a value) and false
     * otherwise
     */
    public boolean isSet(_Fields field) {
      if (field == null) {
        throw new IllegalArgumentException();
      }

      switch (field) {
        case DELAY:
          return isSetDelay();
      }
      throw new IllegalStateException();
    }

    @Override
    public boolean equals(Object that) {
      if (that == null) return false;
      if (that instanceof noReturn_args) return this.equals((noReturn_args) that);
      return false;
    }

    public boolean equals(noReturn_args that) {
      if (that == null) return false;

      boolean this_present_delay = true;
      boolean that_present_delay = true;
      if (this_present_delay || that_present_delay) {
        if (!(this_present_delay && that_present_delay)) return false;
        if (this.delay != that.delay) return false;
      }

      return true;
    }

    @Override
    public int hashCode() {
      return 0;
    }

    @Override
    public int compareTo(noReturn_args other) {
      if (!getClass().equals(other.getClass())) {
        return getClass().getName().compareTo(other.getClass().getName());
      }

      int lastComparison = 0;

      lastComparison = Boolean.valueOf(isSetDelay()).compareTo(other.isSetDelay());
      if (lastComparison != 0) {
        return lastComparison;
      }
      if (isSetDelay()) {
        lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.delay, other.delay);
        if (lastComparison != 0) {
          return lastComparison;
        }
      }
      return 0;
    }

    public _Fields fieldForId(int fieldId) {
      return _Fields.findByThriftId(fieldId);
    }

    public void read(org.apache.thrift.protocol.TProtocol iprot)
        throws org.apache.thrift.TException {
      schemes.get(iprot.getScheme()).getScheme().read(iprot, this);
    }

    public void write(org.apache.thrift.protocol.TProtocol oprot)
        throws org.apache.thrift.TException {
      schemes.get(oprot.getScheme()).getScheme().write(oprot, this);
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder("noReturn_args(");
      boolean first = true;

      sb.append("delay:");
      sb.append(this.delay);
      first = false;
      sb.append(")");
      return sb.toString();
    }

    public void validate() throws org.apache.thrift.TException {
      // check for required fields
      // check for sub-struct validity
    }

    private void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException {
      try {
        write(
            new org.apache.thrift.protocol.TCompactProtocol(
                new org.apache.thrift.transport.TIOStreamTransport(out)));
      } catch (org.apache.thrift.TException te) {
        throw new java.io.IOException(te);
      }
    }

    private void readObject(java.io.ObjectInputStream in)
        throws java.io.IOException, ClassNotFoundException {
      try {
        // it doesn't seem like you should have to do this, but java serialization is wacky, and
        // doesn't call the default constructor.
        __isset_bitfield = 0;
        read(
            new org.apache.thrift.protocol.TCompactProtocol(
                new org.apache.thrift.transport.TIOStreamTransport(in)));
      } catch (org.apache.thrift.TException te) {
        throw new java.io.IOException(te);
      }
    }

    private static class noReturn_argsStandardSchemeFactory implements SchemeFactory {
      public noReturn_argsStandardScheme getScheme() {
        return new noReturn_argsStandardScheme();
      }
    }

    private static class noReturn_argsStandardScheme extends StandardScheme<noReturn_args> {

      public void read(org.apache.thrift.protocol.TProtocol iprot, noReturn_args struct)
          throws org.apache.thrift.TException {
        org.apache.thrift.protocol.TField schemeField;
        iprot.readStructBegin();
        while (true) {
          schemeField = iprot.readFieldBegin();
          if (schemeField.type == org.apache.thrift.protocol.TType.STOP) {
            break;
          }
          switch (schemeField.id) {
            case 1: // DELAY
              if (schemeField.type == org.apache.thrift.protocol.TType.I32) {
                struct.delay = iprot.readI32();
                struct.setDelayIsSet(true);
              } else {
                org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
              }
              break;
            default:
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
          }
          iprot.readFieldEnd();
        }
        iprot.readStructEnd();

        // check for required fields of primitive type, which can't be checked in the validate
        // method
        struct.validate();
      }

      public void write(org.apache.thrift.protocol.TProtocol oprot, noReturn_args struct)
          throws org.apache.thrift.TException {
        struct.validate();

        oprot.writeStructBegin(STRUCT_DESC);
        oprot.writeFieldBegin(DELAY_FIELD_DESC);
        oprot.writeI32(struct.delay);
        oprot.writeFieldEnd();
        oprot.writeFieldStop();
        oprot.writeStructEnd();
      }
    }

    private static class noReturn_argsTupleSchemeFactory implements SchemeFactory {
      public noReturn_argsTupleScheme getScheme() {
        return new noReturn_argsTupleScheme();
      }
    }

    private static class noReturn_argsTupleScheme extends TupleScheme<noReturn_args> {

      @Override
      public void write(org.apache.thrift.protocol.TProtocol prot, noReturn_args struct)
          throws org.apache.thrift.TException {
        TTupleProtocol oprot = (TTupleProtocol) prot;
        BitSet optionals = new BitSet();
        if (struct.isSetDelay()) {
          optionals.set(0);
        }
        oprot.writeBitSet(optionals, 1);
        if (struct.isSetDelay()) {
          oprot.writeI32(struct.delay);
        }
      }

      @Override
      public void read(org.apache.thrift.protocol.TProtocol prot, noReturn_args struct)
          throws org.apache.thrift.TException {
        TTupleProtocol iprot = (TTupleProtocol) prot;
        BitSet incoming = iprot.readBitSet(1);
        if (incoming.get(0)) {
          struct.delay = iprot.readI32();
          struct.setDelayIsSet(true);
        }
      }
    }
  }

  public static class noReturn_result
      implements org.apache.thrift.TBase<noReturn_result, noReturn_result._Fields>,
          java.io.Serializable,
          Cloneable,
          Comparable<noReturn_result> {
    private static final org.apache.thrift.protocol.TStruct STRUCT_DESC =
        new org.apache.thrift.protocol.TStruct("noReturn_result");

    private static final Map<Class<? extends IScheme>, SchemeFactory> schemes =
        new HashMap<Class<? extends IScheme>, SchemeFactory>();

    static {
      schemes.put(StandardScheme.class, new noReturn_resultStandardSchemeFactory());
      schemes.put(TupleScheme.class, new noReturn_resultTupleSchemeFactory());
    }

    /**
     * The set of fields this struct contains, along with convenience methods for finding and
     * manipulating them.
     */
    public enum _Fields implements org.apache.thrift.TFieldIdEnum {
      ;

      private static final Map<String, _Fields> byName = new HashMap<String, _Fields>();

      static {
        for (_Fields field : EnumSet.allOf(_Fields.class)) {
          byName.put(field.getFieldName(), field);
        }
      }

      /** Find the _Fields constant that matches fieldId, or null if its not found. */
      public static _Fields findByThriftId(int fieldId) {
        switch (fieldId) {
          default:
            return null;
        }
      }

      /**
       * Find the _Fields constant that matches fieldId, throwing an exception if it is not found.
       */
      public static _Fields findByThriftIdOrThrow(int fieldId) {
        _Fields fields = findByThriftId(fieldId);
        if (fields == null)
          throw new IllegalArgumentException("Field " + fieldId + " doesn't exist!");
        return fields;
      }

      /** Find the _Fields constant that matches name, or null if its not found. */
      public static _Fields findByName(String name) {
        return byName.get(name);
      }

      private final short _thriftId;
      private final String _fieldName;

      _Fields(short thriftId, String fieldName) {
        _thriftId = thriftId;
        _fieldName = fieldName;
      }

      public short getThriftFieldId() {
        return _thriftId;
      }

      public String getFieldName() {
        return _fieldName;
      }
    }

    public static final Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> metaDataMap;

    static {
      Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> tmpMap =
          new EnumMap<_Fields, org.apache.thrift.meta_data.FieldMetaData>(_Fields.class);
      metaDataMap = Collections.unmodifiableMap(tmpMap);
      org.apache.thrift.meta_data.FieldMetaData.addStructMetaDataMap(
          noReturn_result.class, metaDataMap);
    }

    public noReturn_result() {}

    /** Performs a deep copy on <i>other</i>. */
    public noReturn_result(noReturn_result other) {}

    public noReturn_result deepCopy() {
      return new noReturn_result(this);
    }

    @Override
    public void clear() {}

    public void setFieldValue(_Fields field, Object value) {
      switch (field) {
      }
    }

    public Object getFieldValue(_Fields field) {
      switch (field) {
      }
      throw new IllegalStateException();
    }

    /**
     * Returns true if field corresponding to fieldID is set (has been assigned a value) and false
     * otherwise
     */
    public boolean isSet(_Fields field) {
      if (field == null) {
        throw new IllegalArgumentException();
      }

      switch (field) {
      }
      throw new IllegalStateException();
    }

    @Override
    public boolean equals(Object that) {
      if (that == null) return false;
      if (that instanceof noReturn_result) return this.equals((noReturn_result) that);
      return false;
    }

    public boolean equals(noReturn_result that) {
      if (that == null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return 0;
    }

    @Override
    public int compareTo(noReturn_result other) {
      if (!getClass().equals(other.getClass())) {
        return getClass().getName().compareTo(other.getClass().getName());
      }

      int lastComparison = 0;

      return 0;
    }

    public _Fields fieldForId(int fieldId) {
      return _Fields.findByThriftId(fieldId);
    }

    public void read(org.apache.thrift.protocol.TProtocol iprot)
        throws org.apache.thrift.TException {
      schemes.get(iprot.getScheme()).getScheme().read(iprot, this);
    }

    public void write(org.apache.thrift.protocol.TProtocol oprot)
        throws org.apache.thrift.TException {
      schemes.get(oprot.getScheme()).getScheme().write(oprot, this);
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder("noReturn_result(");
      boolean first = true;

      sb.append(")");
      return sb.toString();
    }

    public void validate() throws org.apache.thrift.TException {
      // check for required fields
      // check for sub-struct validity
    }

    private void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException {
      try {
        write(
            new org.apache.thrift.protocol.TCompactProtocol(
                new org.apache.thrift.transport.TIOStreamTransport(out)));
      } catch (org.apache.thrift.TException te) {
        throw new java.io.IOException(te);
      }
    }

    private void readObject(java.io.ObjectInputStream in)
        throws java.io.IOException, ClassNotFoundException {
      try {
        read(
            new org.apache.thrift.protocol.TCompactProtocol(
                new org.apache.thrift.transport.TIOStreamTransport(in)));
      } catch (org.apache.thrift.TException te) {
        throw new java.io.IOException(te);
      }
    }

    private static class noReturn_resultStandardSchemeFactory implements SchemeFactory {
      public noReturn_resultStandardScheme getScheme() {
        return new noReturn_resultStandardScheme();
      }
    }

    private static class noReturn_resultStandardScheme extends StandardScheme<noReturn_result> {

      public void read(org.apache.thrift.protocol.TProtocol iprot, noReturn_result struct)
          throws org.apache.thrift.TException {
        org.apache.thrift.protocol.TField schemeField;
        iprot.readStructBegin();
        while (true) {
          schemeField = iprot.readFieldBegin();
          if (schemeField.type == org.apache.thrift.protocol.TType.STOP) {
            break;
          }
          switch (schemeField.id) {
            default:
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
          }
          iprot.readFieldEnd();
        }
        iprot.readStructEnd();

        // check for required fields of primitive type, which can't be checked in the validate
        // method
        struct.validate();
      }

      public void write(org.apache.thrift.protocol.TProtocol oprot, noReturn_result struct)
          throws org.apache.thrift.TException {
        struct.validate();

        oprot.writeStructBegin(STRUCT_DESC);
        oprot.writeFieldStop();
        oprot.writeStructEnd();
      }
    }

    private static class noReturn_resultTupleSchemeFactory implements SchemeFactory {
      public noReturn_resultTupleScheme getScheme() {
        return new noReturn_resultTupleScheme();
      }
    }

    private static class noReturn_resultTupleScheme extends TupleScheme<noReturn_result> {

      @Override
      public void write(org.apache.thrift.protocol.TProtocol prot, noReturn_result struct)
          throws org.apache.thrift.TException {
        TTupleProtocol oprot = (TTupleProtocol) prot;
      }

      @Override
      public void read(org.apache.thrift.protocol.TProtocol prot, noReturn_result struct)
          throws org.apache.thrift.TException {
        TTupleProtocol iprot = (TTupleProtocol) prot;
      }
    }
  }

  public static class oneWay_args
      implements org.apache.thrift.TBase<oneWay_args, oneWay_args._Fields>,
          java.io.Serializable,
          Cloneable,
          Comparable<oneWay_args> {
    private static final org.apache.thrift.protocol.TStruct STRUCT_DESC =
        new org.apache.thrift.protocol.TStruct("oneWay_args");

    private static final Map<Class<? extends IScheme>, SchemeFactory> schemes =
        new HashMap<Class<? extends IScheme>, SchemeFactory>();

    static {
      schemes.put(StandardScheme.class, new oneWay_argsStandardSchemeFactory());
      schemes.put(TupleScheme.class, new oneWay_argsTupleSchemeFactory());
    }

    /**
     * The set of fields this struct contains, along with convenience methods for finding and
     * manipulating them.
     */
    public enum _Fields implements org.apache.thrift.TFieldIdEnum {
      ;

      private static final Map<String, _Fields> byName = new HashMap<String, _Fields>();

      static {
        for (_Fields field : EnumSet.allOf(_Fields.class)) {
          byName.put(field.getFieldName(), field);
        }
      }

      /** Find the _Fields constant that matches fieldId, or null if its not found. */
      public static _Fields findByThriftId(int fieldId) {
        switch (fieldId) {
          default:
            return null;
        }
      }

      /**
       * Find the _Fields constant that matches fieldId, throwing an exception if it is not found.
       */
      public static _Fields findByThriftIdOrThrow(int fieldId) {
        _Fields fields = findByThriftId(fieldId);
        if (fields == null)
          throw new IllegalArgumentException("Field " + fieldId + " doesn't exist!");
        return fields;
      }

      /** Find the _Fields constant that matches name, or null if its not found. */
      public static _Fields findByName(String name) {
        return byName.get(name);
      }

      private final short _thriftId;
      private final String _fieldName;

      _Fields(short thriftId, String fieldName) {
        _thriftId = thriftId;
        _fieldName = fieldName;
      }

      public short getThriftFieldId() {
        return _thriftId;
      }

      public String getFieldName() {
        return _fieldName;
      }
    }

    public static final Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> metaDataMap;

    static {
      Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> tmpMap =
          new EnumMap<_Fields, org.apache.thrift.meta_data.FieldMetaData>(_Fields.class);
      metaDataMap = Collections.unmodifiableMap(tmpMap);
      org.apache.thrift.meta_data.FieldMetaData.addStructMetaDataMap(
          oneWay_args.class, metaDataMap);
    }

    public oneWay_args() {}

    /** Performs a deep copy on <i>other</i>. */
    public oneWay_args(oneWay_args other) {}

    public oneWay_args deepCopy() {
      return new oneWay_args(this);
    }

    @Override
    public void clear() {}

    public void setFieldValue(_Fields field, Object value) {
      switch (field) {
      }
    }

    public Object getFieldValue(_Fields field) {
      switch (field) {
      }
      throw new IllegalStateException();
    }

    /**
     * Returns true if field corresponding to fieldID is set (has been assigned a value) and false
     * otherwise
     */
    public boolean isSet(_Fields field) {
      if (field == null) {
        throw new IllegalArgumentException();
      }

      switch (field) {
      }
      throw new IllegalStateException();
    }

    @Override
    public boolean equals(Object that) {
      if (that == null) return false;
      if (that instanceof oneWay_args) return this.equals((oneWay_args) that);
      return false;
    }

    public boolean equals(oneWay_args that) {
      if (that == null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return 0;
    }

    @Override
    public int compareTo(oneWay_args other) {
      if (!getClass().equals(other.getClass())) {
        return getClass().getName().compareTo(other.getClass().getName());
      }

      int lastComparison = 0;

      return 0;
    }

    public _Fields fieldForId(int fieldId) {
      return _Fields.findByThriftId(fieldId);
    }

    public void read(org.apache.thrift.protocol.TProtocol iprot)
        throws org.apache.thrift.TException {
      schemes.get(iprot.getScheme()).getScheme().read(iprot, this);
    }

    public void write(org.apache.thrift.protocol.TProtocol oprot)
        throws org.apache.thrift.TException {
      schemes.get(oprot.getScheme()).getScheme().write(oprot, this);
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder("oneWay_args(");
      boolean first = true;

      sb.append(")");
      return sb.toString();
    }

    public void validate() throws org.apache.thrift.TException {
      // check for required fields
      // check for sub-struct validity
    }

    private void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException {
      try {
        write(
            new org.apache.thrift.protocol.TCompactProtocol(
                new org.apache.thrift.transport.TIOStreamTransport(out)));
      } catch (org.apache.thrift.TException te) {
        throw new java.io.IOException(te);
      }
    }

    private void readObject(java.io.ObjectInputStream in)
        throws java.io.IOException, ClassNotFoundException {
      try {
        read(
            new org.apache.thrift.protocol.TCompactProtocol(
                new org.apache.thrift.transport.TIOStreamTransport(in)));
      } catch (org.apache.thrift.TException te) {
        throw new java.io.IOException(te);
      }
    }

    private static class oneWay_argsStandardSchemeFactory implements SchemeFactory {
      public oneWay_argsStandardScheme getScheme() {
        return new oneWay_argsStandardScheme();
      }
    }

    private static class oneWay_argsStandardScheme extends StandardScheme<oneWay_args> {

      public void read(org.apache.thrift.protocol.TProtocol iprot, oneWay_args struct)
          throws org.apache.thrift.TException {
        org.apache.thrift.protocol.TField schemeField;
        iprot.readStructBegin();
        while (true) {
          schemeField = iprot.readFieldBegin();
          if (schemeField.type == org.apache.thrift.protocol.TType.STOP) {
            break;
          }
          switch (schemeField.id) {
            default:
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
          }
          iprot.readFieldEnd();
        }
        iprot.readStructEnd();

        // check for required fields of primitive type, which can't be checked in the validate
        // method
        struct.validate();
      }

      public void write(org.apache.thrift.protocol.TProtocol oprot, oneWay_args struct)
          throws org.apache.thrift.TException {
        struct.validate();

        oprot.writeStructBegin(STRUCT_DESC);
        oprot.writeFieldStop();
        oprot.writeStructEnd();
      }
    }

    private static class oneWay_argsTupleSchemeFactory implements SchemeFactory {
      public oneWay_argsTupleScheme getScheme() {
        return new oneWay_argsTupleScheme();
      }
    }

    private static class oneWay_argsTupleScheme extends TupleScheme<oneWay_args> {

      @Override
      public void write(org.apache.thrift.protocol.TProtocol prot, oneWay_args struct)
          throws org.apache.thrift.TException {
        TTupleProtocol oprot = (TTupleProtocol) prot;
      }

      @Override
      public void read(org.apache.thrift.protocol.TProtocol prot, oneWay_args struct)
          throws org.apache.thrift.TException {
        TTupleProtocol iprot = (TTupleProtocol) prot;
      }
    }
  }

  public static class oneWayWithError_args
      implements org.apache.thrift.TBase<oneWayWithError_args, oneWayWithError_args._Fields>,
          java.io.Serializable,
          Cloneable,
          Comparable<oneWayWithError_args> {
    private static final org.apache.thrift.protocol.TStruct STRUCT_DESC =
        new org.apache.thrift.protocol.TStruct("oneWayWithError_args");

    private static final Map<Class<? extends IScheme>, SchemeFactory> schemes =
        new HashMap<Class<? extends IScheme>, SchemeFactory>();

    static {
      schemes.put(StandardScheme.class, new oneWayWithError_argsStandardSchemeFactory());
      schemes.put(TupleScheme.class, new oneWayWithError_argsTupleSchemeFactory());
    }

    /**
     * The set of fields this struct contains, along with convenience methods for finding and
     * manipulating them.
     */
    public enum _Fields implements org.apache.thrift.TFieldIdEnum {
      ;

      private static final Map<String, _Fields> byName = new HashMap<String, _Fields>();

      static {
        for (_Fields field : EnumSet.allOf(_Fields.class)) {
          byName.put(field.getFieldName(), field);
        }
      }

      /** Find the _Fields constant that matches fieldId, or null if its not found. */
      public static _Fields findByThriftId(int fieldId) {
        switch (fieldId) {
          default:
            return null;
        }
      }

      /**
       * Find the _Fields constant that matches fieldId, throwing an exception if it is not found.
       */
      public static _Fields findByThriftIdOrThrow(int fieldId) {
        _Fields fields = findByThriftId(fieldId);
        if (fields == null)
          throw new IllegalArgumentException("Field " + fieldId + " doesn't exist!");
        return fields;
      }

      /** Find the _Fields constant that matches name, or null if its not found. */
      public static _Fields findByName(String name) {
        return byName.get(name);
      }

      private final short _thriftId;
      private final String _fieldName;

      _Fields(short thriftId, String fieldName) {
        _thriftId = thriftId;
        _fieldName = fieldName;
      }

      public short getThriftFieldId() {
        return _thriftId;
      }

      public String getFieldName() {
        return _fieldName;
      }
    }

    public static final Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> metaDataMap;

    static {
      Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> tmpMap =
          new EnumMap<_Fields, org.apache.thrift.meta_data.FieldMetaData>(_Fields.class);
      metaDataMap = Collections.unmodifiableMap(tmpMap);
      org.apache.thrift.meta_data.FieldMetaData.addStructMetaDataMap(
          oneWayWithError_args.class, metaDataMap);
    }

    public oneWayWithError_args() {}

    /** Performs a deep copy on <i>other</i>. */
    public oneWayWithError_args(oneWayWithError_args other) {}

    public oneWayWithError_args deepCopy() {
      return new oneWayWithError_args(this);
    }

    @Override
    public void clear() {}

    public void setFieldValue(_Fields field, Object value) {
      switch (field) {
      }
    }

    public Object getFieldValue(_Fields field) {
      switch (field) {
      }
      throw new IllegalStateException();
    }

    /**
     * Returns true if field corresponding to fieldID is set (has been assigned a value) and false
     * otherwise
     */
    public boolean isSet(_Fields field) {
      if (field == null) {
        throw new IllegalArgumentException();
      }

      switch (field) {
      }
      throw new IllegalStateException();
    }

    @Override
    public boolean equals(Object that) {
      if (that == null) return false;
      if (that instanceof oneWayWithError_args) return this.equals((oneWayWithError_args) that);
      return false;
    }

    public boolean equals(oneWayWithError_args that) {
      if (that == null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return 0;
    }

    @Override
    public int compareTo(oneWayWithError_args other) {
      if (!getClass().equals(other.getClass())) {
        return getClass().getName().compareTo(other.getClass().getName());
      }

      int lastComparison = 0;

      return 0;
    }

    public _Fields fieldForId(int fieldId) {
      return _Fields.findByThriftId(fieldId);
    }

    public void read(org.apache.thrift.protocol.TProtocol iprot)
        throws org.apache.thrift.TException {
      schemes.get(iprot.getScheme()).getScheme().read(iprot, this);
    }

    public void write(org.apache.thrift.protocol.TProtocol oprot)
        throws org.apache.thrift.TException {
      schemes.get(oprot.getScheme()).getScheme().write(oprot, this);
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder("oneWayWithError_args(");
      boolean first = true;

      sb.append(")");
      return sb.toString();
    }

    public void validate() throws org.apache.thrift.TException {
      // check for required fields
      // check for sub-struct validity
    }

    private void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException {
      try {
        write(
            new org.apache.thrift.protocol.TCompactProtocol(
                new org.apache.thrift.transport.TIOStreamTransport(out)));
      } catch (org.apache.thrift.TException te) {
        throw new java.io.IOException(te);
      }
    }

    private void readObject(java.io.ObjectInputStream in)
        throws java.io.IOException, ClassNotFoundException {
      try {
        read(
            new org.apache.thrift.protocol.TCompactProtocol(
                new org.apache.thrift.transport.TIOStreamTransport(in)));
      } catch (org.apache.thrift.TException te) {
        throw new java.io.IOException(te);
      }
    }

    private static class oneWayWithError_argsStandardSchemeFactory implements SchemeFactory {
      public oneWayWithError_argsStandardScheme getScheme() {
        return new oneWayWithError_argsStandardScheme();
      }
    }

    private static class oneWayWithError_argsStandardScheme
        extends StandardScheme<oneWayWithError_args> {

      public void read(org.apache.thrift.protocol.TProtocol iprot, oneWayWithError_args struct)
          throws org.apache.thrift.TException {
        org.apache.thrift.protocol.TField schemeField;
        iprot.readStructBegin();
        while (true) {
          schemeField = iprot.readFieldBegin();
          if (schemeField.type == org.apache.thrift.protocol.TType.STOP) {
            break;
          }
          switch (schemeField.id) {
            default:
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
          }
          iprot.readFieldEnd();
        }
        iprot.readStructEnd();

        // check for required fields of primitive type, which can't be checked in the validate
        // method
        struct.validate();
      }

      public void write(org.apache.thrift.protocol.TProtocol oprot, oneWayWithError_args struct)
          throws org.apache.thrift.TException {
        struct.validate();

        oprot.writeStructBegin(STRUCT_DESC);
        oprot.writeFieldStop();
        oprot.writeStructEnd();
      }
    }

    private static class oneWayWithError_argsTupleSchemeFactory implements SchemeFactory {
      public oneWayWithError_argsTupleScheme getScheme() {
        return new oneWayWithError_argsTupleScheme();
      }
    }

    private static class oneWayWithError_argsTupleScheme extends TupleScheme<oneWayWithError_args> {

      @Override
      public void write(org.apache.thrift.protocol.TProtocol prot, oneWayWithError_args struct)
          throws org.apache.thrift.TException {
        TTupleProtocol oprot = (TTupleProtocol) prot;
      }

      @Override
      public void read(org.apache.thrift.protocol.TProtocol prot, oneWayWithError_args struct)
          throws org.apache.thrift.TException {
        TTupleProtocol iprot = (TTupleProtocol) prot;
      }
    }
  }
}
