
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
  var electors: List[Int] = Nil
  var becomeCandidate: Boolean = false
  var leader: Int = 0
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

  def receive = {

    case LiveNodesChanged(nodes) => {
      if (!nodes.contains(leader) && nodes.length > 1) {
        electors = nodes
        if (math.random < 0.75)
          self ! INITIATE
      }
    }

    case INITIATE => {
      //println("Initiation de la négociation")
      status = Candidate
      cand_pred = -1
      cand_succ = -1
      for (i <- 0 until electors.length) {
        if (electors(i) == id) {
          context.parent ! SendElectionMessage(ALG(i), get_neighbor())
          println("ALG message send to " + get_neighbor())
        }
      }
    }

    case ALG(init) => {
      println("ALG message receive from " + init)
      if (status == Passive) {
        println("I'm passive")
        status = Dummy
        for (i <- 0 until electors.length) {
          if (electors(i) == id) {
            context.parent ! SendElectionMessage(ALG(init), get_neighbor())
          }
        }
      } else if (status == Candidate) {
        println("I'm candidate, so you go down")
        cand_pred = init
        if (id > init) {
          if (cand_succ == -1) {
            status = Waiting
            context.parent ! SendElectionMessage(AVS(id), init)
          } else {
            status = Dummy
            context.parent ! SendElectionMessage(AVSRSP(cand_pred), cand_succ)
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
      println("AVS message receive from " + j)
      if (status == Passive) {
        println("I'm passive")
        if (cand_pred == -1) {
          cand_succ = j
        } else {
          context.parent ! SendElectionMessage(AVSRSP(cand_pred), j)
          status = Dummy
        }
      } else if (status == Waiting) {
        println("I'm waiting")
        cand_succ = j
      }
    }

    case AVSRSP(j) => {
      println("AVSRSP receive from " + j)
      if (status == Waiting) {
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
            }
          } else {
            status = Waiting
            context.parent ! SendElectionMessage(AVSRSP(j), cand_succ)
          }
        }
      }
    }
    case _ => println("LiveNodesChanged")
  }

  def get_neighbor(): Int = {
    var nb: Int = id
    for (i <- 0 until electors.length) {
      if (electors(i) == id) {
        nb = (i + 1) % electors.length
      }
    }
    electors(nb)
  }
}
