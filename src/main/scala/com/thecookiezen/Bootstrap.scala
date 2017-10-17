package com.thecookiezen

import akka.NotUsed
import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.{ByteString, Timeout}
import com.thecookiezen.business.containers.control.{Cluster, Host}
import com.thecookiezen.integration.docker.DockerClusterEngine

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

object Bootstrap extends App {
  implicit val timeout = Timeout(5 seconds)

  implicit val system = ActorSystem("clusterEngine")
  implicit val materializer = ActorMaterializer()

//  val clusterActor = system.actorOf(Props(classOf[Cluster], "testing_cluster", 10), "clusterActor")
//
//  clusterActor ! Cluster.StartCluster
//
//  val future1 = clusterActor ? Cluster.SizeOfCluster()
//  println(Await.result(future1, 5 seconds))
//
//  val future2 = clusterActor ? Host.ListContainers("test_label")
//  println(Await.result(future2, 5 seconds))
//
//  val future3 = clusterActor ? Cluster.ListHosts()
//  println(Await.result(future3, 5 seconds))
//
//  clusterActor ! Cluster.AddNewHost("1.32","http://localhost:2375")

  val http = Http(system)

  val testing = new DockerClusterEngine("1.32","localhost:2375", http.singleRequest(_))

  private val eventualInitialized: Future[Host.Initialized] = testing.getRunningContainers("test")
  private val initialized: Host.Initialized = Await.result(eventualInitialized, 5 seconds)
  println(initialized)

  private val future: Future[Source[ByteString, NotUsed]] = testing.log("462cc24ae03f")

  future.map(source => source.runForeach(byteString => println(byteString.utf8String)))
}
