
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
  var leader: Int = -1
  def receive = {
    case PrintStatus =>
      //println("Le leader est " + leader)
      scheduler.scheduleOnce(Const.STATUS_PRINTING_DELAY, self, PrintStatus)

    case LeaderChanged(i) =>
      leader = i
      println("Le leader est " + i)
    case LiveNodesChanged(nodes) =>
    //print("[ ");
    //nodes.foreach { n => print(n + " ") };
    //print(" ]\n");
  }

  case object PrintStatus

  self ! PrintStatus

}
