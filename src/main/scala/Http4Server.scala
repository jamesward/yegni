import zio.*
import zio.interop.catz.*

import org.http4s.HttpApp
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.server.Server
import org.http4s.server.blaze.BlazeServerBuilder

import scala.Unit

import java.lang.Throwable

object Http4Server:

  type Http4Server = Has[Server]

  def createHttp4Server(app: HttpApp[Task], port: Port) =
    def build(implicit runtime: Runtime[ZEnv]) =
      BlazeServerBuilder[Task](runtime.platform.executor.asEC)
        .bindHttp(port.toInt, "localhost")
        .withHttpApp(app)
        .serve
        .compile
        .drain

    ZIO.runtime[ZEnv].flatMap(build)

object Http4Client:

  type Http4Client = Has[Client[Task]]

  def createHttp4Client: ZManaged[ZEnv, Throwable, Client[Task]] =
    def build(implicit runtime: Runtime[ZEnv]) =
      BlazeClientBuilder[Task](runtime.platform.executor.asEC).resource.toManagedZIO

    ZManaged.runtime[ZEnv].flatMap(build)

  def createHttp4ClientZLayer: ZLayer[ZEnv, Throwable, Http4Client] =
    ZLayer.fromManaged(createHttp4Client)