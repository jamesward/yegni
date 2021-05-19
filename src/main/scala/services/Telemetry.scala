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
    Tracer, Span, SpanContext, TraceFlags, TraceState, TraceId, SpanId, SpanBuilder
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
  Array,
  &,
}
import scala.Predef.summon
import zio.{
  Layer,
  Has,
  ZLayer,
  ZIO,
  Runtime,
  Ref
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

type TelemetryContext = Has[TelemetryContext.Service]
object TelemetryContext:
  /** The service for using telemetry. */
  trait Service:
    def currentContext: Ref[Context]
    def extract[T : TextMapGetter](request: T): ZIO[TelemetryContext, Nothing, Unit]
    def inject[T : TextMapSetter](request: T): ZIO[TelemetryContext, Nothing, Unit]
    def tracer(name: String, version: String = "1.0"): ZIO[TelemetryContext, Nothing, Tracer]
    
  private def currentContext: ZIO[TelemetryContext, Nothing, Ref[Context]] =
    ZIO.accessM[TelemetryContext](x => ZIO.succeed(x.get.currentContext))
  /** Extracts distributed context from an incoming request. */
  def extract[T: TextMapGetter](request: T): ZIO[TelemetryContext, Nothing, Unit] =
    ZIO.accessM[TelemetryContext](_.get.extract[T](request))
  /** Injects distributed context propagation headers into an outgoing request. */
  def inject[T: TextMapSetter](request: T): ZIO[TelemetryContext, Nothing, Unit] =
    ZIO.accessM[TelemetryContext](_.get.inject[T](request))
  /** Wraps a given effect with an HTTP span. */
  def httpSpan[Env, Error, R](exchange: HttpExchange)(handler: ZIO[TelemetryContext & Env, Error, R]): ZIO[TelemetryContext & Env, Error, R] =
      // Note: This has to be done "raw" because of how ZIO handles `accessM`, we can't preserve our environment through an acccesM.
      for
        ctxRef <- currentContext
        initialContext <-ctxRef.get
        tracer <- ZIO.accessM[TelemetryContext](_.get.tracer("zio-yegni-http"))
        span <- ZIO.effectTotal(makeHttpSpan(exchange, initialContext, tracer))
        _ <- ZIO.effectTotal(java.lang.System.err.println(s"Processing HTTP request: $span"))
        _ <- ctxRef.update(c => span.storeInContext(c))
        result <- handler.ensuring(ZIO.effectTotal(span.end()))  // TODO - more cleanup?
      yield result


  private def makeHttpSpan(exchange: HttpExchange, context: Context, tracer: Tracer): Span =
    val spanBuilder = 
      tracer.spanBuilder(exchange.getRequestURI.toString)
      .setParent(context)
    spanBuilder.setAttribute("component", "http")
    spanBuilder.setAttribute("http.method", exchange.getRequestMethod)
    spanBuilder.setAttribute("http.scheme", "http")
    spanBuilder.setAttribute("http.host", exchange.getLocalAddress.getHostString)
    for
      (attr, env) <- scala.Seq(("service.name", "K_SERVICE"), ("service.version", "K_REVISION"))
      value <- scala.sys.env.get(env)
    do spanBuilder.setAttribute(attr, value)
    // TODO - more attributes/semantic conventions
    spanBuilder.startSpan()

  /** OpenTelemetry instrumentation of TelemetryContext. */
  class OtelService(override val currentContext: Ref[Context], otel: OpenTelemetry)
      extends Service:
    override def extract[T : TextMapGetter](request: T): ZIO[TelemetryContext, Nothing, Unit] =
      currentContext update { ctx =>
        val propagator = otel.getPropagators.getTextMapPropagator
        propagator.extract(ctx, request, summon[TextMapGetter[T]])
      }
    override def inject[T : TextMapSetter](request: T): ZIO[TelemetryContext, Nothing, Unit] =
      for
        ctx <- currentContext.get
      yield 
        val propagator = otel.getPropagators.getTextMapPropagator
        propagator.inject(ctx, request, summon[TextMapSetter[T]])
    override def tracer(name: String, version: String = "1.0"): ZIO[TelemetryContext, Nothing, Tracer] =
      ZIO.succeed(otel.getTracer(name, version))

  /** Live layer of our service. */
  val liveOtel: ZLayer[Any, Nothing, TelemetryContext] = 
    val effect: ZIO[Any, Nothing, TelemetryContext.Service] =
      for 
        ctxRef <- Ref.make(Context.root)
      yield OtelService(ctxRef, makeTracePipeline())
    ZLayer.fromEffect(effect)
      
    

private def makeTracePipeline(): OpenTelemetry =
  def propagators = ContextPropagators.create(
        TextMapPropagator.composite(
          W3CTraceContextPropagator.getInstance(),
          CloudTraceContextPropagation))
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
    textPropagator.extract(Context.current, exchange, summon[TextMapGetter[HttpExchange]])

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
    


  /** Injects the distributed trace context into HTTP requests. */
  def injectContext(req: HttpRequest.Builder): HttpRequest.Builder =
    textPropagator.inject(Context.current, req, summon[TextMapSetter[HttpRequest.Builder]])
    req


object CloudTraceContextPropagation extends TextMapPropagator:
  val myKey = "x-cloud-trace-context"
  override val fields = scala.collection.immutable.Seq(myKey).asJava
  override def inject[C](context: Context, carrier: C, setter: TextMapSetter[C]): Unit =
    if context != null && setter != null then
      val current = Span.fromContext(context)
      if current.getSpanContext.isValid then
        val sampled = if current.getSpanContext.isSampled then 1 else 0
        val spanIdAsUnsignedLong = java.lang.Long.toUnsignedString(java.lang.Long.parseUnsignedLong(current.getSpanContext.getSpanId, 16))
        val value = s"${current.getSpanContext.getTraceId}/${spanIdAsUnsignedLong};o=${sampled}"
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
      val span = rest.split(";o=") match
        case Array(s, sampled) => s
        case Array(s) => s
        case _ => ""
      val correctedSpan = SpanId.fromLong(java.lang.Long.parseUnsignedLong(span))
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


given TextMapGetter[HttpExchange] with
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

given TextMapSetter[HttpRequest.Builder] with
  override def set(ctx: HttpRequest.Builder, key: String, value: String): Unit =
    ctx.header(key, value)