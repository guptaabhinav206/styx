/*
  Copyright (C) 2013-2019 Expedia Inc.

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */
package com.hotels.styx.proxy;

import com.google.common.collect.ImmutableList;
import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.client.HttpClient;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.client.StyxHttpClient;
import com.hotels.styx.infrastructure.configuration.yaml.YamlConfig;
import com.hotels.styx.routing.handlers.HttpInterceptorPipeline;
import com.hotels.styx.server.HttpConnectorConfig;
import com.hotels.styx.server.HttpServer;
import com.hotels.styx.server.StandardHttpRouter;
import com.hotels.styx.server.netty.NettyServerBuilder;
import com.hotels.styx.server.netty.NettyServerBuilderSpec;
import com.hotels.styx.server.netty.NettyServerConfig;
import com.hotels.styx.server.netty.ServerConnector;
import com.hotels.styx.server.netty.WebServerConnectorFactory;
import org.testng.annotations.Test;

import java.io.IOException;

import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.common.StyxFutures.await;
import static com.hotels.styx.common.io.ResourceFactory.newResource;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;

public class StyxProxyTest extends SSLSetup {
    final HttpClient client = new StyxHttpClient.Builder()
            .build();

    public static void main(String[] args) {
        NettyServerConfig config = new YamlConfig(newResource("classpath:default.yaml"))
                .get("proxy", NettyServerConfig.class).get();

        HttpServer server = new NettyServerBuilderSpec()
                .toNettyServerBuilder(config)
                .build();

        server.startAsync().awaitRunning();
    }

    @Test
    public void startsAndStopsAServer() {
        HttpServer server = new NettyServerBuilder()
                .setHttpConnector(connector(0))
                .build();

        server.startAsync().awaitRunning();
        assertThat("Server should be running", server.isRunning());

        server.stopAsync().awaitTerminated();
        assertThat("Server should not be running", !server.isRunning());
    }

    @Test
    public void startsServerWithHttpConnector() {
        HttpInterceptor echoInterceptor = (request, chain) -> textResponse("Response from http connector");

        HttpServer server = NettyServerBuilder.newBuilder()
                .setHttpConnector(connector(0))
                .handlerFactory(() -> new HttpInterceptorPipeline(ImmutableList.of(echoInterceptor), new StandardHttpRouter(), false))
                .build();
        server.startAsync().awaitRunning();
        assertThat("Server should be running", server.isRunning());

        HttpResponse secureResponse = get("http://localhost:" + server.httpAddress().getPort());
        assertThat(secureResponse.bodyAs(UTF_8), containsString("Response from http connector"));

        server.stopAsync().awaitTerminated();
        assertThat("Server should not be running", !server.isRunning());
    }

    private Eventual<LiveHttpResponse> textResponse(String body) {
        return Eventual.of(HttpResponse.response(OK)
                .body("Response from http connector", UTF_8)
                .build()
                .stream());
    }

    private ServerConnector connector(int port) {
        return connector(new HttpConnectorConfig(port));
    }

    private ServerConnector connector(HttpConnectorConfig config) {
        return new WebServerConnectorFactory().create(config);
    }

    @Test(enabled = false)
    public void startsServerWithBothHttpAndHttpsConnectors() throws IOException {
        HttpServer server = NettyServerBuilder.newBuilder()
                .setHttpConnector(connector(0))
                .build();

        server.startAsync().awaitRunning();
        assertThat("Server should be running", server.isRunning());

        System.out.println("server is running: " + server.isRunning());

        HttpResponse clearResponse = get("http://localhost:8080/search?q=fanta");
        assertThat(clearResponse.bodyAs(UTF_8), containsString("Response from http Connector"));

        HttpResponse secureResponse = get("https://localhost:8443/secure");
        assertThat(secureResponse.bodyAs(UTF_8), containsString("Response from https Connector"));


        server.stopAsync().awaitTerminated();
        assertThat("Server should not be running", !server.isRunning());
    }

    private HttpResponse get(String uri) {
        HttpRequest secureRequest = HttpRequest.get(uri).build();
        return execute(secureRequest);
    }

    private HttpResponse execute(HttpRequest request) {
        return await(client.sendRequest(request));
    }
}
