/**
 * Copyright (C) 2013-2017 Expedia Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hotels.styx.admin

import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.{Files, Paths}

import com.google.common.io.Files.createTempDir
import com.hotels.styx.api.HttpRequest
import com.hotels.styx.proxy.backends.file.FileBackedBackendServicesRegistry
import com.hotels.styx.support.ResourcePaths.fixturesHome
import com.hotels.styx.support.configuration.{BackendService, ConnectionPoolSettings, HealthCheckConfig, Origin, Origins, StyxConfig}
import com.hotels.styx.{StyxClientSupplier, StyxServer, StyxServerSupport}
import io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST
import org.scalatest.concurrent.Eventually
import org.scalatest.{BeforeAndAfterAll, FunSpec, ShouldMatchers}

import scala.collection.JavaConverters._
import scala.concurrent.duration._

class OriginsReloadCommandSpec extends FunSpec
  with StyxServerSupport
  with StyxClientSupplier
  with ShouldMatchers
  with BeforeAndAfterAll
  with Eventually {

  val tempDir = createTempDir()
  val originsOk = fixturesHome(classOf[OriginsReloadCommandSpec], "/conf/origins/origins-correct.yml")
  val originsNok = fixturesHome(classOf[OriginsReloadCommandSpec], "/conf/origins/origins-incorrect.yml")
  val styxOriginsFile = Paths.get(tempDir.toString, "origins.yml")
  var styxServer: StyxServer = _

  it("Responds with BAD_REQUEST when the origins cannot be read") {
    val fileBasedBackendsRegistry = new FileBackedBackendServicesRegistry(styxOriginsFile.toString)
    styxServer = StyxConfig().startServer(fileBasedBackendsRegistry)
    styxServer.isRunning should be(true)

    Files.copy(originsNok, styxOriginsFile, REPLACE_EXISTING)

    val resp = decodedRequest(HttpRequest.Builder.post(styxServer.adminURL("/admin/tasks/origins/reload")).build())
    resp.status() should be(BAD_REQUEST)

    BackendService.fromJava(fileBasedBackendsRegistry.get().asScala.head) should be(
      BackendService(
        appId = "app",
        path = "/",
        connectionPoolConfig = ConnectionPoolSettings(
          maxConnectionsPerHost = 45,
          maxPendingConnectionsPerHost = 15,
          socketTimeoutMillis = 120000,
          connectTimeoutMillis = 1000,
          pendingConnectionTimeoutMillis = 8000
        ),
        healthCheckConfig = HealthCheckConfig(
          uri = Some("/version.txt"),
          interval = 5.seconds
        ),
        responseTimeout = 60.seconds,
        origins = Origins(
          Origin("localhost", 9090, appId = "app", id = "app1"),
          Origin("localhost", 9091, appId = "app", id = "app2")
        )
      ))
  }

    override protected def beforeAll(): Unit = {
      Files.copy(originsOk, styxOriginsFile)
    }

    override protected def afterAll(): Unit = {
      styxServer.stopAsync().awaitTerminated()
      Files.delete(styxOriginsFile)
      Files.delete(tempDir.toPath)
    }
  }
