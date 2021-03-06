/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.jsonrpc.websocket;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.JsonRpcMethod;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcError;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.methods.WebSocketRpcRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

@RunWith(VertxUnitRunner.class)
public class WebSocketRequestHandlerTest {

  private static final int VERTX_AWAIT_TIMEOUT_MILLIS = 10000;

  private Vertx vertx;
  private WebSocketRequestHandler handler;
  private JsonRpcMethod jsonRpcMethodMock;
  private final Map<String, JsonRpcMethod> methods = new HashMap<>();

  @Before
  public void before(final TestContext context) {
    vertx = Vertx.vertx();

    jsonRpcMethodMock = mock(JsonRpcMethod.class);

    methods.put("eth_x", jsonRpcMethodMock);
    handler = new WebSocketRequestHandler(vertx, methods);
  }

  @After
  public void after(final TestContext context) {
    Mockito.reset(jsonRpcMethodMock);
    vertx.close(context.asyncAssertSuccess());
  }

  @Test
  public void handlerDeliversResponseSuccessfully(final TestContext context) {
    final Async async = context.async();

    final JsonObject requestJson = new JsonObject().put("id", 1).put("method", "eth_x");
    final JsonRpcRequest expectedRequest = requestJson.mapTo(WebSocketRpcRequest.class);
    final JsonRpcSuccessResponse expectedResponse =
        new JsonRpcSuccessResponse(expectedRequest.getId(), null);
    when(jsonRpcMethodMock.response(eq(expectedRequest))).thenReturn(expectedResponse);

    final String websocketId = UUID.randomUUID().toString();

    vertx
        .eventBus()
        .consumer(websocketId)
        .handler(
            msg -> {
              context.assertEquals(Json.encode(expectedResponse), msg.body());
              async.complete();
            })
        .completionHandler(v -> handler.handle(websocketId, Buffer.buffer(requestJson.toString())));

    async.awaitSuccess(WebSocketRequestHandlerTest.VERTX_AWAIT_TIMEOUT_MILLIS);
  }

  @Test
  public void jsonDecodeFailureShouldRespondInvalidRequest(final TestContext context) {
    final Async async = context.async();

    final String websocketId = UUID.randomUUID().toString();

    vertx
        .eventBus()
        .consumer(websocketId)
        .handler(
            msg -> {
              context.assertEquals(Json.encode(JsonRpcError.INVALID_REQUEST), msg.body());
              verifyZeroInteractions(jsonRpcMethodMock);
              async.complete();
            })
        .completionHandler(v -> handler.handle(websocketId, Buffer.buffer("")));

    async.awaitSuccess(VERTX_AWAIT_TIMEOUT_MILLIS);
  }

  @Test
  public void objectMapperFailureShouldRespondInvalidRequest(final TestContext context) {
    final Async async = context.async();

    final String websocketId = UUID.randomUUID().toString();

    vertx
        .eventBus()
        .consumer(websocketId)
        .handler(
            msg -> {
              context.assertEquals(Json.encode(JsonRpcError.INVALID_REQUEST), msg.body());
              verifyZeroInteractions(jsonRpcMethodMock);
              async.complete();
            })
        .completionHandler(v -> handler.handle(websocketId, Buffer.buffer("{}")));

    async.awaitSuccess(VERTX_AWAIT_TIMEOUT_MILLIS);
  }

  @Test
  public void absentMethodShouldRespondMethodNotFound(final TestContext context) {
    final Async async = context.async();

    final JsonObject requestJson =
        new JsonObject().put("id", 1).put("method", "eth_nonexistentMethod");

    final String websocketId = UUID.randomUUID().toString();

    vertx
        .eventBus()
        .consumer(websocketId)
        .handler(
            msg -> {
              context.assertEquals(Json.encode(JsonRpcError.METHOD_NOT_FOUND), msg.body());
              async.complete();
            })
        .completionHandler(v -> handler.handle(websocketId, Buffer.buffer(requestJson.toString())));

    async.awaitSuccess(WebSocketRequestHandlerTest.VERTX_AWAIT_TIMEOUT_MILLIS);
  }

  @Test
  public void onExceptionProcessingRequestShouldRespondInternalError(final TestContext context) {
    final Async async = context.async();

    final JsonObject requestJson = new JsonObject().put("id", 1).put("method", "eth_x");
    final JsonRpcRequest expectedRequest = requestJson.mapTo(WebSocketRpcRequest.class);
    when(jsonRpcMethodMock.response(eq(expectedRequest))).thenThrow(new RuntimeException());

    final String websocketId = UUID.randomUUID().toString();

    vertx
        .eventBus()
        .consumer(websocketId)
        .handler(
            msg -> {
              context.assertEquals(Json.encode(JsonRpcError.INTERNAL_ERROR), msg.body());
              async.complete();
            })
        .completionHandler(v -> handler.handle(websocketId, Buffer.buffer(requestJson.toString())));

    async.awaitSuccess(WebSocketRequestHandlerTest.VERTX_AWAIT_TIMEOUT_MILLIS);
  }
}
