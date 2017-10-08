package com.thecookiezen.containers

import akka.actor.{ActorRef, FSM, Stash}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import akka.util.ByteString
import com.thecookiezen.containers.DockerHost.{Uninitialized, _}

class DockerHost(dockerApiVersion: String, dockerDaemonUrl: String) extends FSM[DockerDaemonState, Data] with Stash {

  import akka.pattern.pipe
  import context.dispatcher

  final implicit val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(context.system))

  val http = Http(context.system)

  startWith(Initialization, Uninitialized)

  when(Initialization) {
    case Event(HttpResponse(StatusCodes.OK, _, entity, _), Uninitialized) =>
      entity.dataBytes.runFold(ByteString(""))(_ ++ _).foreach { body =>
        log.info("Got response, body: " + body.utf8String)
      }
      goto(Active) using ContainersList(Seq.empty)
    case Event(resp @ HttpResponse(code, _, _, _), Uninitialized) =>
        log.info("Request failed, response code: " + code)
      resp.discardEntityBytes()
      goto(Active) using ContainersList(Seq.empty)
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
      http.singleRequest(HttpRequest(uri = dockerDaemonUrl)).pipeTo(self)
    }
  }

  initialize()
}

object DockerHost {

  sealed trait DockerDaemonState
  case object Initialization extends DockerDaemonState
  case object Active extends DockerDaemonState

  sealed trait Data
  case object Uninitialized extends Data
  case class ContainersList(containers: Seq[String]) extends Data

  final case class Deploy(host: ActorRef)
  final case class Initialized(containers: Seq[String])
  final case class ListContainers(label: String)
  final case class CreateContainer(image: String, repository: String, metadata: Map[String, String])

}
