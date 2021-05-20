package services

import zio.{
  App,
  ExitCode,
  Schedule,
  ZEnv,
  ZIO,
  ZLayer,
}

// Helper method to do effect hedging.
extension [Env, Err, R](effect: ZIO[Env, Err, R])
  /**
   * Hedges a retry of this effect against the 50th-percentile latency of the effect.
   */
  def hedge: ZIO[Env, Err, R] = 
    // TODO - implement.
    effect