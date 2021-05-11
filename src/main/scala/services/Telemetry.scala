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
    Tracer
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




opaque type Span = io.opentelemetry.api.trace.Span
type Telemetry = Has[Telemetry.Service]
object Telemetry:
  object MyTextMapGetter extends TextMapGetter[HttpExchange]:
    override def keys(ctx: HttpExchange) = ctx.getRequestHeaders.keySet
    override def get(ctx: HttpExchange, key: String): String =
      if ctx.getRequestHeaders.containsKey(key) then 
        ctx.getRequestHeaders.get(key).get(0)
      else ""
  object MyTextMapSetter extends TextMapSetter[HttpExchange]:
    override def set(ctx: HttpExchange, key: String, value: String): Unit =
      ctx.getResponseHeaders.set(key,value)
  def tracedHandler(tracer: Tracer, otel: OpenTelemetry)(handler: HttpHandler): HttpHandler =
    exchange =>
      val textPropagator = otel.getPropagators.getTextMapPropagator
      textPropagator.extract(Context.current, exchange, MyTextMapGetter)
      val span = tracer.spanBuilder(exchange.getRequestURI.toString).startSpan()
      // TODO - context + w3c trace propogation.
      span.setAttribute("component", "http")
      span.setAttribute("http.method", exchange.getRequestMethod)
      span.setAttribute("http.scheme", "http")      
      try 
        val current = span.makeCurrent()
        try handler.handle(exchange)
        finally current.close()
      finally span.end()
      


  trait Service:
    /** Constructs an open telemetry span. */
    def span(description: String): Span
    

  def span(description: String): ZIO[Telemetry, Nothing, Span] =
    ZIO.accessM(service => ZIO.effectTotal(service.get.span(description)))

  def spanAttr(span: Span, key: String, value: String): ZIO[Any, Nothing, Span] =
    ZIO.effectTotal(span.setAttribute(key, value))
  def spanAttr(span: Span, key: String, value: Long): ZIO[Any, Nothing, Span] =
    ZIO.effectTotal(span.setAttribute(key, value))
  def spanAttr(span: Span, key: String, value: Double): ZIO[Any, Nothing, Span] =
    ZIO.effectTotal(span.setAttribute(key, value))


  def endSpan(span: Span): ZIO[Any, Nothing, Unit] =
    ZIO.effectTotal(span.end())

  
  def openTelemetry(tracerName: String = "zio-telemetry", tracerVersion: String = "1.0"): ZLayer[Any, IOException, Has[Telemetry.Service]] =
    val config = TraceConfiguration.builder().build()
    try
      val exporter = TraceExporter.createWithConfiguration(config)
      val batcher = BatchSpanProcessor.builder(exporter).build()
      val sdk = OpenTelemetrySdk.builder()
        .setTracerProvider(
          SdkTracerProvider.builder().addSpanProcessor(batcher).build()
        ).build()
      ZLayer.succeed(new Service:
        val tracer = sdk.getTracer(tracerName, tracerVersion)
        override def span(description: String): Span =
          tracer.spanBuilder(description).startSpan()
      )        
    catch
      case e: IOException => ZLayer.fail(e)




      