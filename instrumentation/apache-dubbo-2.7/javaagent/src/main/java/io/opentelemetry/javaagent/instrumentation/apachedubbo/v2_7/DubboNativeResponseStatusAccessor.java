/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachedubbo.v2_7;

import io.opentelemetry.instrumentation.apachedubbo.v2_7.internal.DubboNativeResponseStatus;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import org.apache.dubbo.remoting.exchange.Request;
import org.apache.dubbo.remoting.exchange.Response;
import org.apache.dubbo.remoting.exchange.support.DefaultFuture;
import org.apache.dubbo.rpc.RpcInvocation;

final class DubboNativeResponseStatusAccessor implements DubboNativeResponseStatus.Accessor {

  static final DubboNativeResponseStatusAccessor INSTANCE = new DubboNativeResponseStatusAccessor();

  private static final VirtualField<RpcInvocation, InvocationState> INVOCATION_STATE =
      VirtualField.find(RpcInvocation.class, InvocationState.class);

  private static final VirtualField<Request, InvocationState> CLIENT_REQUEST_STATE =
      VirtualField.find(Request.class, InvocationState.class);

  private static final VirtualField<Response, ResponseState> RESPONSE_STATE =
      VirtualField.find(Response.class, ResponseState.class);

  private static final ThreadLocal<Request> CURRENT_SERVER_REQUEST = new ThreadLocal<>();
  private static final ThreadLocal<Response> CURRENT_SERVER_RESPONSE = new ThreadLocal<>();
  private static final ThreadLocal<RpcInvocation> CURRENT_CLIENT_INVOCATION = new ThreadLocal<>();

  private static final ClassValue<Map<Byte, String>> RESPONSE_STATUS_NAMES =
      new ClassValue<Map<Byte, String>>() {
        @Override
        protected Map<Byte, String> computeValue(Class<?> type) {
          Map<Byte, String> names = new HashMap<>();
          for (Field field : type.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == byte.class) {
              try {
                names.put(field.getByte(null), field.getName());
              } catch (IllegalAccessException ignored) {
                // Only public status constants are available through this class.
              }
            }
          }
          return Collections.unmodifiableMap(names);
        }
      };

  static void captureClientStatus(Object future, Response response) {
    if (!DubboSingletons.captureNativeResponseStatus()) {
      return;
    }
    Request request = ((DefaultFuture) future).getRequest();
    InvocationState state = CLIENT_REQUEST_STATE.get(request);
    if (state == null) {
      Object data = request.getData();
      if (!(data instanceof RpcInvocation)) {
        return;
      }
      state = invocationState((RpcInvocation) data);
    }
    state.clientStatusCode = statusName(response);
  }

  @Nullable
  @Override
  public Object enterClientInvocation(Object invocationObject) {
    if (!DubboSingletons.captureNativeResponseStatus()
        || !(invocationObject instanceof RpcInvocation)) {
      return null;
    }
    RpcInvocation invocation = (RpcInvocation) invocationObject;
    RpcInvocation previous = CURRENT_CLIENT_INVOCATION.get();
    CURRENT_CLIENT_INVOCATION.set(invocation);
    invocationState(invocation);
    return new ClientInvocationScope(previous);
  }

  @Override
  public void exitClientInvocation(Object scope) {
    ClientInvocationScope clientScope = (ClientInvocationScope) scope;
    if (clientScope.previous == null) {
      CURRENT_CLIENT_INVOCATION.remove();
    } else {
      CURRENT_CLIENT_INVOCATION.set(clientScope.previous);
    }
  }

  static void bindClientRequest(Object requestObject) {
    INSTANCE.associateClientRequest(requestObject);
  }

  @Override
  public void associateClientRequest(Object requestObject) {
    if (!(requestObject instanceof Request)) {
      return;
    }
    RpcInvocation invocation = CURRENT_CLIENT_INVOCATION.get();
    if (invocation == null) {
      return;
    }
    Request request = (Request) requestObject;
    InvocationState state = invocationState(invocation);
    CLIENT_REQUEST_STATE.set(request, state);
    state.clientRequest = new WeakReference<>(request);
  }

  @Nullable
  static RequestScope enterServerRequest(Request request) {
    if (!DubboSingletons.captureNativeResponseStatus()) {
      return null;
    }
    Request previous = CURRENT_SERVER_REQUEST.get();
    Response previousResponse = CURRENT_SERVER_RESPONSE.get();
    CURRENT_SERVER_REQUEST.set(request);
    CURRENT_SERVER_RESPONSE.remove();
    return new RequestScope(previous, previousResponse);
  }

  static void exitServerRequest(RequestScope scope) {
    if (scope.previous == null) {
      CURRENT_SERVER_REQUEST.remove();
    } else {
      CURRENT_SERVER_REQUEST.set(scope.previous);
    }
    if (scope.previousResponse == null) {
      CURRENT_SERVER_RESPONSE.remove();
    } else {
      CURRENT_SERVER_RESPONSE.set(scope.previousResponse);
    }
  }

  static void associateServerResponse(Response response) {
    if (!DubboSingletons.captureNativeResponseStatus()) {
      return;
    }
    Request request = CURRENT_SERVER_REQUEST.get();
    if (request == null || request.getId() != response.getId()) {
      return;
    }
    RESPONSE_STATE.set(response, new ResponseState());
    CURRENT_SERVER_RESPONSE.set(response);
  }

  @Override
  public void associateServerInvocation(Object invocationObject) {
    if (!(invocationObject instanceof RpcInvocation)) {
      return;
    }
    Response response = CURRENT_SERVER_RESPONSE.get();
    ResponseState responseState = response == null ? null : RESPONSE_STATE.get(response);
    if (responseState == null) {
      return;
    }
    RpcInvocation invocation = (RpcInvocation) invocationObject;
    responseState.invocation = invocation;
    invocationState(invocation).serverResponse = new WeakReference<>(response);
  }

  static void captureServerStatus(Response response) {
    ResponseState state = RESPONSE_STATE.get(response);
    if (state == null) {
      return;
    }
    state.statusCode = statusName(response);
    Runnable completion = state.completion;
    if (completion != null) {
      try {
        completion.run();
      } finally {
        clearResponse(response, state);
      }
    } else {
      clearResponse(response, state);
    }
  }

  @Nullable
  @Override
  public String getStatusCode(Object invocationObject) {
    if (!(invocationObject instanceof RpcInvocation)) {
      return null;
    }
    InvocationState state = INVOCATION_STATE.get((RpcInvocation) invocationObject);
    if (state == null) {
      return null;
    }
    if (state.clientStatusCode != null) {
      return state.clientStatusCode;
    }
    Response response = response(state);
    ResponseState responseState = response == null ? null : RESPONSE_STATE.get(response);
    return responseState == null ? null : responseState.statusCode;
  }

  @Override
  public void clear(Object invocationObject) {
    if (!(invocationObject instanceof RpcInvocation)) {
      return;
    }
    RpcInvocation invocation = (RpcInvocation) invocationObject;
    InvocationState state = INVOCATION_STATE.get(invocation);
    INVOCATION_STATE.set(invocation, null);
    if (state != null) {
      Request request = clientRequest(state);
      if (request != null && CLIENT_REQUEST_STATE.get(request) == state) {
        CLIENT_REQUEST_STATE.set(request, null);
      }
      Response response = response(state);
      ResponseState responseState = response == null ? null : RESPONSE_STATE.get(response);
      if (response != null && responseState != null) {
        clearResponse(response, responseState);
      }
    }
  }

  @Override
  public boolean deferServerCompletion(Object invocationObject, Runnable completion) {
    if (!(invocationObject instanceof RpcInvocation)) {
      return false;
    }
    InvocationState state = INVOCATION_STATE.get((RpcInvocation) invocationObject);
    Response response = state == null ? null : response(state);
    ResponseState responseState = response == null ? null : RESPONSE_STATE.get(response);
    if (responseState == null || responseState.statusCode != null) {
      return false;
    }
    responseState.completion = completion;
    return true;
  }

  private static InvocationState invocationState(RpcInvocation invocation) {
    InvocationState state = INVOCATION_STATE.get(invocation);
    if (state == null) {
      state = new InvocationState();
      INVOCATION_STATE.set(invocation, state);
    }
    return state;
  }

  @Nullable
  private static Response response(InvocationState state) {
    return state.serverResponse == null ? null : state.serverResponse.get();
  }

  @Nullable
  private static Request clientRequest(InvocationState state) {
    return state.clientRequest == null ? null : state.clientRequest.get();
  }

  private static String statusName(Response response) {
    return RESPONSE_STATUS_NAMES.get(response.getClass()).getOrDefault(response.getStatus(), null);
  }

  private static void clearResponse(Response response, ResponseState state) {
    if (RESPONSE_STATE.get(response) == state) {
      RESPONSE_STATE.set(response, null);
    }
    if (state.invocation != null) {
      INVOCATION_STATE.set(state.invocation, null);
    }
  }

  static final class RequestScope {

    @Nullable private final Request previous;
    @Nullable private final Response previousResponse;

    private RequestScope(@Nullable Request previous, @Nullable Response previousResponse) {
      this.previous = previous;
      this.previousResponse = previousResponse;
    }
  }

  private static final class ClientInvocationScope {

    @Nullable private final RpcInvocation previous;

    private ClientInvocationScope(@Nullable RpcInvocation previous) {
      this.previous = previous;
    }
  }

  private static final class InvocationState {

    @Nullable private String clientStatusCode;
    @Nullable private WeakReference<Request> clientRequest;
    @Nullable private WeakReference<Response> serverResponse;
  }

  private static final class ResponseState {

    @Nullable private RpcInvocation invocation;
    @Nullable private String statusCode;
    @Nullable private Runnable completion;
  }

  private DubboNativeResponseStatusAccessor() {}
}
