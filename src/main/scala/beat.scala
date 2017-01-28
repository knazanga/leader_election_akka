import com.typesafe.config._

import scala.tools.nsc.interpreter.IMain
import scala.tools.nsc.Settings
import scala.io.Source
import scala.concurrent.duration._

import akka.actor._
import akka.io._
import akka.util.Timeout
import akka.util.ByteString

import java.net._
import collection.JavaConversions._

//My adds
import scala.concurrent.ExecutionContext.Implicits._

import math._

/*
 Heart beat agent:
 This actor sends a message Beat or LeaderBeat to all nodes on the network.
 If this node is the leader, the LeaderBeat message is sent. If this node
 is not the leader, a Beat message is sent.
 */

class BeatActor(id: Int) extends Actor {
  var leader: Int = -1

  case object Tick

  val scheduler = context.system.scheduler
  val node = context.actorSelection("/user/Node")

  def receive = {
    case Tick => {
      if (id == leader) {
        node ! OutGoingMessage(LeaderBeat(id))
        node ! LeaderBeat(id)
      } else {
        node ! OutGoingMessage(Beat(id))
        node ! Beat(id)
      }
      scheduler.scheduleOnce(Const.HEART_BEAT_PERIOD, self, Tick)
    }
    case LeaderChanged(id) => {
      leader = id
    }
    case _ =>
  }

  self ! Tick

}
