import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.opentelemetry.trace.{TraceConfiguration, TraceExporter}

import scala.main
import scala.Console.println

@main def graaler =
  val creds = GoogleCredentials.getApplicationDefault()
  println(creds)
  val config = TraceConfiguration.builder.build()
  println(config)
  val exporter = TraceExporter.createWithConfiguration(config)
  println(exporter)