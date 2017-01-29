
import com.typesafe.config._

import scala.tools.nsc.interpreter.IMain
import scala.tools.nsc.Settings
import scala.io.Source
import scala.concurrent.duration._

import akka.actor._
import akka.io._
import akka.util.Timeout
import akka.util.ByteString

import scala.concurrent.ExecutionContext.Implicits._

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

class ElectionAgent(id: Int, nb_nodes: Int) extends Actor {
  var electors: List[Int] = Nil
  var becomeCandidate: Boolean = false
  var leader: Int = -1
  var cand_pred: Int = -1
  var cand_succ: Int = -1
  var status: ElectionStatus = Passive
  case object INITIATE

  abstract class ElectionStatus
  case object Passive extends ElectionStatus
  case object Candidate extends ElectionStatus
  case object Dummy extends ElectionStatus
  case object Leader extends ElectionStatus
  case object Waiting extends ElectionStatus

  val scheduler = context.system.scheduler
  case object InitLeader

  def receive = {
    case LiveNodesChanged(nodes) => {
      electors = nodes
      if (leader < 0) {
        scheduler.scheduleOnce(Const.LEADER_NEGOCIATION_DELAY, self, InitLeader)
      } else if (!nodes.contains(leader)) {
        self ! INITIATE
      }
    }

    case INITIATE => {
      status = Candidate
      cand_pred = -1
      cand_succ = -1
      if (electors.length == 1) {
        println("Yes, I'm the leader")
        leader = id
        context.parent ! LeaderChanged(leader)
      } else {
        context.parent ! SendElectionMessage(ALG(id), get_next())
        println("ALG Message sent to " + get_next())
      }
    }
    case ALG(init) => {
      println("ALG Message received from " + get_next())
      if (status.equals(Passive)) {
        status = Dummy
        context.parent ! SendElectionMessage(ALG(init), get_next())
        println("ALG Message sent to " + get_next())
      } else if (status.equals(Candidate)) {
        cand_pred = init
        if (id > init) {
          if (cand_succ == -1) {
            status = Waiting
            context.parent ! SendElectionMessage(AVS(id), init)
            println("AVS Message sent to " + init)
          } else {
            context.parent ! SendElectionMessage(AVSRSP(cand_pred), cand_succ)
            println("AVSRSP Message sent to " + cand_succ)
            status = Dummy
          }
        } else if (id == init) {
          println("Yes, I'm the new leader!!!")
          status = Leader
          leader = id
          context.parent ! LeaderChanged(id)
        }
      }
    }

    case AVS(j) => {
      println("AVS message received from " + j)
      if (status.equals(Candidate)) {
        println("I'm candidate")
        if (cand_pred == -1) {
          cand_succ = j
        } else {
          context.parent ! SendElectionMessage(AVSRSP(cand_pred), j)
          println("AVSRSP message send to " + j)
          status = Dummy
        }
      } else if (status.equals(Waiting)) {
        println("I'm waiting")
        cand_succ = j
      }
    }

    case AVSRSP(j) => {
      println("AVSRSP received from " + j)
      if (status.equals(Waiting)) {
        println("I'm waiting")
        if (id == j) {
          println("Yes, I'm the new leader!!!")
          status = Leader
          leader = id
          context.parent ! LeaderChanged(id)
        } else {
          cand_pred = j
          if (cand_succ == -1) {
            if (j < id) {
              status = Waiting
              context.parent ! SendElectionMessage(AVS(id), j)
              println("AVS message send to " + j)
            }
          } else {
            context.parent ! SendElectionMessage(AVSRSP(j), cand_succ)
            println("AVSRSP message send to " + cand_succ)
            status = Dummy
          }
        }
      }
    }
    case InitLeader => {
      if (leader < 0) {
        leader = id
        context.parent ! LeaderChanged(leader)
      }
    }
    case LeaderBeat(i) =>
      leader = i
    case _ =>
  }

  def get_neighbor(): Int = {
    var nb: Int = id
    for (i <- 0 until electors.length) {
      if (electors(i) == id) {
        nb = electors((i + 1) % electors.length)
      }
    }
    nb
  }

  def get_next(): Int = {
    var isIt: Boolean = false
    var next: Int = id
    while (!isIt) {
      next = (next + 1) % electors.length
      isIt = electors.contains(next)
    }
    next
  }
}
