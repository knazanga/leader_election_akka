
import com.typesafe.config._

import scala.concurrent.duration._

import akka.actor.Props;
import java.util.concurrent.TimeUnit;

import akka.actor._

import java.net._
import collection.JavaConversions._

//My adds
import scala.concurrent.ExecutionContext.Implicits._

import math._

/*
 Status agent:
 This actor prints the status of this node periodically.
*/
class Interface(id: Int, m: Terminal) extends Actor {
  var scheduler = context.system.scheduler

  def receive = {
    case PrintStatus =>
      scheduler.scheduleOnce(Const.STATUS_PRINTING_DELAY, self, PrintStatus)

    case LeaderChanged(id) =>
      println("Le leader est " + id)
    case LiveNodesChanged(nodes) =>
      println("Le nombre de noeuds a changé")
  }

  case object PrintStatus

  self ! PrintStatus

}
