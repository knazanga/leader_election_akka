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

import scala.concurrent.Await;

/* Constants */
object Const {

  private final val TIME_BASE = 50 milliseconds

  final val HEART_BEAT_PERIOD = 40 * TIME_BASE
  final val SENTINEL_PERIOD = 80 * TIME_BASE
  final val LEADER_NEGOCIATION_DELAY = 100 * TIME_BASE
  final val STATUS_PRINTING_DELAY = 20 * TIME_BASE
}

/* Data classes */
case class Terminal(nom: String, ip: String, port: Int)

/* Internal messages : exchanged between agent on the same node*/
case object Watch
case object Init
case object Quit
case class LeaderChanged(leader_id: Int)
case class NetworkReady(net_agent: ActorRef)
case class LiveNodesChanged(live_nodes: List[Int])

/* External messages : exchanged between nodes in the network*/
abstract class Message
case class Beat(i: Int) extends Message
case class ALG(i: Int) extends Message //Send by Candidate to its successor
case class AVS(j: Int) extends Message
case class AVSRSP(k: Int) extends Message
case class LeaderBeat(id: Int) extends Message

case class OutGoingMessage(m: Message) extends Message
case class SendElectionMessage(m: Message, dest: Int) extends Message
case class ElectionMessage(m: Message, dest: Int) extends Message

/* Main object*/
object Project extends App {

  class Node(n_id: Int, nodes: List[Terminal]) extends Actor {

    // node work
    val beat = context.actorOf(Props(new BeatActor(n_id)), name = "beat")
    val checker = context.actorOf(Props(new Sentinel(nodes.length)), name = "sentinel")
    val display = context.actorOf(Props(new Interface(n_id, nodes(n_id))), name = "display")
    val elector = context.actorOf(Props(new ElectionAgent(n_id, nodes.length)), name = "elector")

    def get_node(n: Int) = {
      try {
        val actorRef = Await.result(context.system.actorSelection("akka.tcp://MySystem" + n + "@" + nodes(n).ip + ":" + nodes(n).port + "/user/Node")
          .resolveOne((200).millis), (200).millis)
        actorRef
      } catch {
        case e: Exception =>
          null
      }
    }

    def receive = {
      case Beat(i) =>
        checker ! Beat(i)
      case LeaderBeat(i) =>
        checker ! LeaderBeat(i)
      case OutGoingMessage(m) =>
        for (d <- nodes.indices) {
          if (d != n_id) {
            val node_dest = get_node(d)
            if (node_dest != null) {
              node_dest ! m
            }
          }
        }

      case SendElectionMessage(m, dest) =>
        get_node(dest) ! ElectionMessage(m, dest)
      case ElectionMessage(m, dest) =>
        elector ! m
      case LeaderChanged(leader) =>
        beat ! LeaderChanged(leader)
        display ! LeaderChanged(leader)
      case LiveNodesChanged(new_live_nodes) =>
        display ! LiveNodesChanged(new_live_nodes)
        elector ! LiveNodesChanged(new_live_nodes)
    }
  }

  override def main(args: Array[String]): Unit = {
    val local_name = args(0)
    val nodes = List(
      Terminal("Mac0", "127.0.0.1", 6000),
      Terminal("Mac1", "127.0.0.1", 6001),
      Terminal("Mac2", "127.0.0.1", 6002),
      Terminal("Mac3", "127.0.0.1", 6003))
    var live_nodes = List.empty[Int]
    val node_id: Int = nodes.indexWhere(m => m.nom.equals(local_name))
    if (node_id < 0) {
      println("This name was not found in the config")
    } else {
      val system = ActorSystem("MySystem" + node_id, ConfigFactory.load().getConfig("system" + node_id))
      val actor = system.actorOf(Props(new Node(node_id, nodes)), "Node")
    }
  }
}

