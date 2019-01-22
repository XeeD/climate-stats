package gaisma

import akka.actor.Actor
import akka.event.Logging

class Parser extends Actor {
  val log = Logging(context.system, this)

  def receive: Receive = {
    case gaismaDocument: GaismaDocument[StatsEntity] =>
      log.info(s"${gaismaDocument} parsing started")
      sender() ! gaismaDocument.parse
  }
}