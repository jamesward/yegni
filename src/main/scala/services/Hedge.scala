package services

import zio.{
  App,
  ExitCode,
  Schedule,
  ZEnv,
  ZIO,
  ZLayer,
}
import zio.duration.*
import zio.clock.Clock
import scala.&

// Helper method to do effect hedging.
extension [Env, Err, R](effect: ZIO[Env, Err, R])
  /**
   * Hedges a retry of this effect against a predetermined latency-goal.
   */
  def hedge(duration: Duration = 1.seconds): ZIO[Env & Clock, Err, R] =
    //  TODO - update hedged request to keep track of average
    // latency instead of always waiting N seconds.
    val delayedEffect =
      for
        _ <- ZIO.sleep(duration)
        result <- effect
      yield result
    effect.disconnect.race(delayedEffect.disconnect)