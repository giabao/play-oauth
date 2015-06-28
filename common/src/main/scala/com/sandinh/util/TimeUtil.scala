package com.sandinh.util

import java.time.Instant
import scala.concurrent.duration.FiniteDuration

object TimeUtil {
  implicit class InstantEx(val u: Instant) extends AnyVal {
    @inline def +(d: FiniteDuration): Instant = u plusNanos d.toNanos
    @inline def -(d: FiniteDuration): Instant = u minusNanos d.toNanos
    @inline def <(other: Instant) = u isBefore other
  }
}
