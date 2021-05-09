package services


import com.google.cloud.opentelemetry.trace.{
    TraceConfiguration,
    TraceExporter
}
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.{
    Tracer
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


opaque type Span = io.opentelemetry.api.trace.Span
type Telemetry = Has[Telemetry.Service]
object Telemetry:
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