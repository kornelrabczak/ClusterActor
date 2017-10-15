package com.thecookiezen.business.containers.control

import akka.actor.FSM.Failure
import akka.actor.{ActorRef, FSM, Stash}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import com.thecookiezen.business.containers.boundary.ClusterEngine
import com.thecookiezen.business.containers.control.Host.{Uninitialized, _}

import scala.concurrent.duration._

trait Host extends FSM[HostState, Data] with Stash {

  this: ClusterEngine =>

  import akka.pattern.pipe
  import context.dispatcher

  final implicit val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(context.system))

  startWith(Initialization, Uninitialized)

  when(Initialization, stateTimeout = 10 seconds) {
    case Event(Initialized(containers), _) =>
      log.info("Host initialized: found {} containers", containers)
      goto(Active) using ContainersList(containers)
    case Event(StateTimeout, Uninitialized) =>
      log.info("DockerHost {}: failed initialization stage", self.path.name)
      stop(reason = Failure(new IllegalStateException("Containers loading failed")))
    case Event(_, _) => {
      stash()
      stay
    }
  }

  when(Active) {
    case Event(ListContainers(label), ContainersList(containers)) => {
      stay replying containers
    }
  }

  whenUnhandled {
    case Event(e, s) =>
      log.warning("received unhandled request {} in state {}/{}", e, stateName, s)
      stay
  }

  onTransition {
    case _ -> Initialization => {
      log.info("Initialization of new docker host")
      getRunningContainers("test").pipeTo(self)
    }
  }

  initialize()
}

object Host {

  sealed trait HostState
  case object Initialization extends HostState
  case object Active extends HostState

  sealed trait Data
  case object Uninitialized extends Data
  case class ContainersList(containers: Seq[String]) extends Data

  final case class Deploy(host: ActorRef)
  final case class Initialized(containers: Seq[String])
  final case class ListContainers(label: String)
  final case class CreateContainer(image: String, repository: String, metadata: Map[String, String])

}
