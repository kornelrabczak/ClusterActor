package com.thecookiezen.containers

import java.time.LocalDateTime
import java.util.UUID

import akka.actor.{FSM, Props, Terminated}
import akka.pattern.ask
import akka.routing.{ActorRefRoutee, RoundRobinRoutingLogic, Router}
import akka.util
import akka.util.Timeout
import com.thecookiezen.containers.Cluster._
import com.thecookiezen.containers.Deployment.DeployJob
import com.thecookiezen.containers.DockerHost.ListContainers

import scala.concurrent.Future
import scala.concurrent.duration._

class Cluster(name: String, maxContainers: Int = 50) extends FSM[ClusterState, Data] {

  implicit val timeout: util.Timeout = Timeout(5 seconds)
  import scala.concurrent.ExecutionContext.Implicits.global

  val router = Router(RoundRobinRoutingLogic())

  startWith(Stopped, Uninitialized)

  when(Stopped) {
    case Event(StartCluster, Uninitialized) => goto(Empty) using HostsList(Seq.empty)
  }

  when(Empty) {
    case Event(dockerHost: AddDockerHost, HostsList(_)) => {
      goto(NewHostInitialization) using HostsList(hosts = Seq(
        HostIdentity(dockerApiVersion = dockerHost.dockerApiVersion, dockerDaemonUrl = dockerHost.dockerDaemonUrl)))
    }
  }

  when(NewHostInitialization) {
    case Event(NewHostInitialized(), _) => goto(Active)
  }

  when(Active) {
    case Event(dockerHost: AddDockerHost, h @ HostsList(hosts)) => {
      goto(NewHostInitialization) using h.copy(hosts = hosts :+ HostIdentity(
        dockerApiVersion = dockerHost.dockerApiVersion, dockerDaemonUrl = dockerHost.dockerDaemonUrl))
    }
    case Event(deployment @ DeployJob, HostsList(hosts)) if hosts.nonEmpty => {
      router.route(deployment, self)
      stay
    }
  }

  onTransition {
    case Stopped -> Empty => {
      log.info("Cluster {} started...", name)
    }
    case _ -> NewHostInitialization => {
      log.info("Cluster {} creating new actor", name)

      val newHost = nextStateData match {
        case HostsList(hosts) => hosts.find(host => context.child(host.id).isEmpty)
        case _ => None
      }

      createNewHostAndRegisterAsRoute(newHost)
      self ! NewHostInitialized()
    }
    case NewHostInitialization -> Active => {
      log.info("Cluster {} get new host, hosts: {}", name, nextStateData.asInstanceOf[HostsList].hosts)
    }
  }

  private def createNewHostAndRegisterAsRoute(newHost: Option[HostIdentity]) = {
    newHost match {
      case Some(host) =>
        val child = context.actorOf(Props(classOf[DockerHost], host.dockerApiVersion, host.dockerDaemonUrl), host.id)
        context.watch(child)
        router.addRoutee(ActorRefRoutee(child))
    }
  }

  whenUnhandled {
    case Event(Terminated(child), h @ HostsList(hosts)) => {
      log.info("Cluster {}: child {} was terminated", name, child)
      router.removeRoutee(child)
      stay using HostsList(hosts.filterNot(host => host.id == child.path.name))
    }
    case Event(SizeOfCluster(), HostsList(hosts)) => stay replying hosts.size
    case Event(label @ ListContainers(_), _) => stay replying Future.sequence(context.children.map(child => child ? label.copy()))
    case Event(ListHosts(), HostsList(hosts)) => stay replying hosts.map(_.id)
    case Event(GetHost(id), _) => stay replying context.child(id).map(actor => actor.path.name).getOrElse("There is no actor with specified id.")
    case Event(e, s) =>
      log.warning("received unhandled request {} in state {}/{}", e, stateName, s)
      stay
  }

  initialize()
}

object Cluster {
  sealed trait ClusterState
  case object Empty extends ClusterState
  case object Stopped extends ClusterState
  case object Active extends ClusterState
  case object NewHostInitialization extends ClusterState

  sealed trait Data
  case object Uninitialized extends Data
  case class HostsList(hosts: Seq[HostIdentity]) extends Data

  final case class StartCluster()
  final case class SizeOfCluster()
  final case class ListHosts()
  final case class GetHost(id: String)
  final case class AddDockerHost(dockerApiVersion: String, dockerDaemonUrl: String, created: LocalDateTime = LocalDateTime.now())
  final case class HostIdentity(id: String = UUID.randomUUID().toString, dockerApiVersion: String, dockerDaemonUrl: String)
  final case class NewHostInitialized()
}

object Deployment {
  final case class DeployJob()
}
