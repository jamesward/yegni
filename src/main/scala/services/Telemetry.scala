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
    Tracer, Span, SpanContext, TraceFlags, TraceState, TraceId, SpanId
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
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import java.lang.String
import java.io.IOException
import scala.{
  Any,
  Nothing,
  Unit,
  Int,
  Long,
  Double,
  Array
}
import zio.{
  Layer,
  Has,
  ZLayer,
  ZIO,
  Runtime
}
import zio.internal.Executor
import scala.compiletime.{
  erasedValue
}
import scala.concurrent.ExecutionContext
import java.lang.Throwable
import java.lang.Runnable



import com.sun.net.httpserver.{HttpExchange, HttpHandler}
import java.net.http.HttpRequest

import scala.jdk.CollectionConverters._

// type TelemetryContext = Has[TelemetryContext]
// object TelemetryContext:
//   trait Service:
//     def extract[T](request: T): ZIO[TelemetryContext, Nothing, Unit]


//   def current: ZIO{Any, Nothing, TelemetryContext}
  


object HttpServerInstrumentation:
  private def propagators = ContextPropagators.create(
        TextMapPropagator.composite(
          W3CTraceContextPropagator.getInstance(),
          CloudTraceContextPropagation))
  private def makeTracePipeline(): OpenTelemetry =
    try
      // todo: this silently fails if the project is not set
      val config = TraceConfiguration.builder.build()
      val exporter = TraceExporter.createWithConfiguration(config)
      val batcher = BatchSpanProcessor.builder(exporter).setMaxQueueSize(2).build()
      OpenTelemetrySdk.builder()
      .setPropagators(propagators)
      .setTracerProvider(
        SdkTracerProvider.builder().addSpanProcessor(batcher).build()).build()
    catch
      case e =>
        java.lang.System.err.println("Failed to configure OpenTelemetry to talk to GCP.")
        e.printStackTrace()
        java.lang.System.err.println("... Continuing without telemetry.")
        OpenTelemetrySdk.builder().setPropagators(propagators).build

  val sdk = makeTracePipeline()
  val httpTracer = sdk.getTracer("zio-jvm-http", "1.0")
  val textPropagator = sdk.getPropagators.getTextMapPropagator

  /** Injects context propagation onto ZIO. */
  def instrumentZio[Env](r: Runtime[Env]): Runtime[Env] =
    // For now, just force all threads to run synchronously.
    r.withExecutor(Executor.fromExecutionContext(Int.MaxValue)(
      new ExecutionContext:
        override def execute(r: Runnable): Unit = r.run()
        override def reportFailure(cause: Throwable): Unit = cause.printStackTrace()
    ))

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
    span.setAttribute("http.host", exchange.getLocalAddress.getHostString)
    for
      (attr, env) <- scala.Seq(("service.name", "K_SERVICE"), ("service.version", "K_REVISION"))
      value <- scala.sys.env.get(env)
    do span.setAttribute(attr, value)
    // TODO - more attributes/semantic conventions
    span


  object MyTextMapGetter extends TextMapGetter[HttpExchange]:
    override def keys(ctx: HttpExchange) =
      ctx.getRequestHeaders.keySet.asScala.map(normalizeFrom).asJava
    override def get(ctx: HttpExchange, key: String): String =
      if ctx.getRequestHeaders.containsKey(key) then
        ctx.getRequestHeaders.get(normalizeTo(key)).get(0)
      else ""
    private def normalizeFrom(key: String): String = key.toLowerCase
    private def normalizeTo(key: String): String =
      if key.length > 0 then
        s"${java.lang.Character.toUpperCase(key.charAt(0))}${key.substring(1).toLowerCase}"
      else key
    


  /** Injects the distributed trace context into HTTP requests. */
  def injectContext(req: HttpRequest.Builder): HttpRequest.Builder =
    textPropagator.inject(Context.current, req, MyTextMapSetter)
    req
  object MyTextMapSetter extends TextMapSetter[HttpRequest.Builder]:
    override def set(ctx: HttpRequest.Builder, key: String, value: String): Unit =
      ctx.header(key, value)


object CloudTraceContextPropagation extends TextMapPropagator:
  val myKey = "x-cloud-trace-context"
  override val fields = scala.collection.immutable.Seq(myKey).asJava
  override def inject[C](context: Context, carrier: C, setter: TextMapSetter[C]): Unit =
    if context != null && setter != null then
      val current = Span.fromContext(context)
      if current.getSpanContext.isValid then
        val sampled = if current.getSpanContext.isSampled then 1 else 0
        val value = s"${current.getSpanContext.getTraceId}/${current.getSpanContext.getSpanId};o=${sampled}"
        setter.set(carrier, myKey, value)
  override def extract[C](context: Context, carrier: C, getter: TextMapGetter[C]): Context =
    java.lang.System.err.println(s"Extracting context with keys: ${getter.keys(carrier).asScala}")
    // TODO - extract span.
    if 
      context != null &&
      getter != null &&
      // If anotehr propagator has filled context, don't also fill here.
      !Span.fromContext(context).getSpanContext.isRemote &&
      !getter.get(carrier, myKey).isEmpty
    then
      java.lang.System.err.println("Found cloud trace context, extracting...")
      val value = getter.get(carrier, myKey)
      java.lang.System.err.println(s"X-Cloud-Trace-Context: ${value}")
      // Now parse the value.
      val Array(trace, rest) = value.split("/")      
      val Array(span, sampled) = rest.split(";o=")
      val correctedSpan = SpanId.fromLong(java.lang.Long.parseLong(span))
      if !TraceId.isValid(trace) then
        java.lang.System.err.println(s"Invalid trace id: $trace")
      else if !SpanId.isValid(correctedSpan) then
        java.lang.System.err.println(s"Invalid span id: $span")
      else ()
      // TODO - pull sampling bit.
      val parent = SpanContext.createFromRemoteParent(trace, correctedSpan, TraceFlags.getSampled, TraceState.getDefault)
      java.lang.System.err.println(s"Distributed trace: $parent")
      val contextSpan = Span.wrap(parent)
      java.lang.System.err.println(s"ContextSpan: ${contextSpan}")
      context.`with`(contextSpan)
    else context