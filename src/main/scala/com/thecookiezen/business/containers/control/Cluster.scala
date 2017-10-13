package com.thecookiezen.business.containers.control

import java.time.LocalDateTime
import java.util.UUID

import akka.actor.{FSM, Props, Terminated}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.pattern.ask
import akka.routing.{ActorRefRoutee, RoundRobinRoutingLogic, Router}
import akka.stream.ActorMaterializer
import akka.util
import akka.util.Timeout
import com.thecookiezen.business.containers.control.Cluster._
import com.thecookiezen.business.containers.control.Deployment.DeployJob
import com.thecookiezen.business.containers.control.Host.ListContainers
import com.thecookiezen.integration.docker.DockerHost

import scala.concurrent.Future
import scala.concurrent.duration._

class Cluster(name: String, maxContainers: Int = 50) extends FSM[ClusterState, Data] {

  import context.dispatcher

  implicit val timeout: util.Timeout = Timeout(5 seconds)

  implicit val materializer = ActorMaterializer()

  val http = Http(context.system)

  val router = Router(RoundRobinRoutingLogic())

  startWith(Stopped, Uninitialized)

  when(Stopped) {
    case Event(StartCluster, Uninitialized) => goto(Empty) using HostsList(Seq.empty)
  }

  when(Empty) {
    case Event(host: AddNewHost, HostsList(_)) => {
      goto(NewHostInitialization) using HostsList(hosts = Seq(
        HostIdentity(apiVersion = host.apiVersion, daemonUrl = host.daemonUrl)))
    }
  }

  when(NewHostInitialization) {
    case Event(NewHostInitialized(), _) => goto(Active)
  }

  when(Active) {
    case Event(host: AddNewHost, h @ HostsList(hosts)) => {
      goto(NewHostInitialization) using h.copy(hosts = hosts :+ HostIdentity(
        apiVersion = host.apiVersion, daemonUrl = host.daemonUrl))
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
        val child = context.actorOf(getProperties(host, (req: HttpRequest) => http.singleRequest(req)), host.id)
        context.watch(child)
        router.addRoutee(ActorRefRoutee(child))
    }
  }

  private def getProperties(host: HostIdentity, http: HttpRequest => Future[HttpResponse]): Props = {
    Props(classOf[DockerHost], host.apiVersion, host.daemonUrl, http, dispatcher, materializer)
  }

  whenUnhandled {
    case Event(Terminated(child), h @ HostsList(hosts)) => {
      log.info("Cluster {}: child {} was terminated", name, child.path.name)
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
  final case class AddNewHost(apiVersion: String, daemonUrl: String, created: LocalDateTime = LocalDateTime.now())
  final case class HostIdentity(id: String = UUID.randomUUID().toString, apiVersion: String, daemonUrl: String)
  final case class NewHostInitialized()
}

object Deployment {
  final case class DeployJob()
}
