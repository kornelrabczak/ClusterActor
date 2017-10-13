package com.thecookiezen

import akka.actor.{ActorSystem, Props}
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.thecookiezen.business.containers.control.{Cluster, Host}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

object Bootstrap extends App {
  implicit val timeout = Timeout(5 seconds)

  implicit val system = ActorSystem("clusterEngine")
  implicit val materializer = ActorMaterializer()

  val clusterActor = system.actorOf(Props(classOf[Cluster], "testing_cluster", 10), "clusterActor")

  clusterActor ! Cluster.StartCluster

  val future1 = clusterActor ? Cluster.SizeOfCluster()
  println(Await.result(future1, 5 seconds))

  val future2 = clusterActor ? Host.ListContainers("test_label")
  println(Await.result(future2, 5 seconds))

  val future3 = clusterActor ? Cluster.ListHosts()
  println(Await.result(future3, 5 seconds))

  val future4 = clusterActor ? Cluster.AddNewHost("1.31","http://localhost:2375")
  println(Await.result(future4, 5 seconds))
}
