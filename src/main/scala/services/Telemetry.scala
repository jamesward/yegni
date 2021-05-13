package services


import com.google.cloud.opentelemetry.trace.{
    TraceConfiguration,
    TraceExporter
}
import io.opentelemetry.api.{
  OpenTelemetry,
  GlobalOpenTelemetry
}
import io.opentelemetry.api.trace.{
    Tracer, Span
}
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.{
  TextMapGetter,
  TextMapSetter,
  TextMapPropagator
}
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.`export`.BatchSpanProcessor
import java.lang.String
import java.io.IOException
import scala.{
  Any,
  Nothing,
  Unit,
  Int,
  Long,
  Double
}
import zio.{
  Layer,
  Has,
  ZLayer,
  ZIO
}
import scala.compiletime.{
  erasedValue
}



import com.sun.net.httpserver.{HttpExchange, HttpHandler}


object HttpServerInstrumentation:
  private def makeTracePipeline(): OpenTelemetry =
    // todo: this silently fails if the project is not set
    val config = TraceConfiguration.builder.build()
    val exporter = TraceExporter.createWithConfiguration(config)
    val batcher = BatchSpanProcessor.builder(exporter).setMaxQueueSize(2).build()
    OpenTelemetrySdk.builder().setTracerProvider(
      SdkTracerProvider.builder().addSpanProcessor(batcher).build()).build()
  val sdk = makeTracePipeline()
  val httpTracer = sdk.getTracer("zio-jvm-http", "1.0")
  val textPropagator = sdk.getPropagators.getTextMapPropagator

  /** Extract distributed trace/span ids from http headers. */
  def extractContext(exchange: HttpExchange) =
    textPropagator.extract(Context.current, exchange, MyTextMapGetter)

  /** Bounds the handling of an HTTP span.
   *
   *  This is not zio friendly as it expects the complete handling of the
   *  HTTP exchange to happen within `work`.
   */
  def startHttpServerSpan(exchange: HttpExchange): Span =
    val span = httpTracer.spanBuilder(exchange.getRequestURI.toString).startSpan()
    span.setAttribute("component", "http")
    span.setAttribute("http.method", exchange.getRequestMethod)
    span.setAttribute("http.scheme", "http")
    // TODO - more attributes/semantic conventions
    span


  object MyTextMapGetter extends TextMapGetter[HttpExchange]:
    override def keys(ctx: HttpExchange) = ctx.getRequestHeaders.keySet
    override def get(ctx: HttpExchange, key: String): String =
      if ctx.getRequestHeaders.containsKey(key) then
        ctx.getRequestHeaders.get(key).get(0)
      else ""
  object MyTextMapSetter extends TextMapSetter[HttpExchange]:
    override def set(ctx: HttpExchange, key: String, value: String): Unit =
      ctx.getResponseHeaders.set(key,value)


