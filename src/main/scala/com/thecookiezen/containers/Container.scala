package com.thecookiezen.containers

import akka.actor.FSM
import com.thecookiezen.containers.Container.{ContainerState, Created, Data, Uninitialized}

class Container(name: String) extends FSM[ContainerState, Data] {
  startWith(Created, Uninitialized)

  whenUnhandled {
    case Event(e, s) =>
      log.warning("received unhandled request {} in state {}/{}", e, stateName, s)
      stay replying s"my name is $name"
  }

  initialize()
}

object Container {
  sealed trait Data
  case object Uninitialized extends Data

  sealed trait ContainerState
  case object Created extends ContainerState
  case object Restarting extends ContainerState
  case object Running extends ContainerState
  case object Paused extends ContainerState
  case object Exited extends ContainerState
  case object Dead extends ContainerState
}
