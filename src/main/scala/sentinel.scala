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
 Sentinel agent:
 Monitors the nodes in the network. Whenever a node joins or leaves the network,
 this actor will send the LiveNodesChanged message to all the local actors.
 If the leader node changes, this actor will send the LeaderChanged message to
 all the local actors.
 */
class Sentinel(nb_nodes: Int) extends Actor {
  val node = context.actorSelection("/user/Node")
  val local = context.actorSelection("/user/Node/*")
  var allNodes: List[Int] = Nil
  var aliveNodes: List[Int] = Nil
  var leader: Int = 0

  val scheduler = context.system.scheduler
  case object Check

  def receive = {

    case Beat(src) => {
      if (allNodes.contains(src)) {
        aliveNodes = src :: aliveNodes
      } else {
        allNodes = src :: allNodes
        aliveNodes = src :: aliveNodes
        node ! LiveNodesChanged(aliveNodes)
      }
    }
    case LeaderBeat(src) =>
      if (src != leader) {
        leader = src
        node ! LeaderChanged(src)
      }
    case Check => {
      if (listChange(allNodes, aliveNodes)) {
        node ! LiveNodesChanged(aliveNodes)
        allNodes = aliveNodes
      }
      aliveNodes = Nil
      scheduler.scheduleOnce(Const.SENTINEL_PERIOD, self, Check)
    }
    case _ =>
  }

  self ! Check

  def listChange(base: List[Int], newList: List[Int]) = {
    var test: Boolean = false
    base.foreach { a =>
      if (!newList.contains(a)) {
        test = true
      }
    }
    test
  }
}