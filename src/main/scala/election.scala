
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

import math._

/*
 Election agent:
 This actor implements the leader negociation protocol. When a node joins or leaves the network,
 this actor will receive a LiveNodesChanged message. If the leader node is no longer on the network,
 this actor initiates the negociation protocol, and starts a leader agent on the node elected as
 leader.
*/

class ElectionAgent(id: Int, nb_players: Int) extends Actor {
  var leader: Int = 0
  case object INITIATE

  abstract class ElectionStatus
  case object Passive extends ElectionStatus
  case object Candidate extends ElectionStatus
  case object Dummy extends ElectionStatus
  case object Leader extends ElectionStatus
  case object Waiting extends ElectionStatus

  def receive = {

    case LiveNodesChanged(nodes) => {
      if (!nodes.contains(leader)) {
      }
    }
    case _ => println("LiveNodesChanged")

  }

}
