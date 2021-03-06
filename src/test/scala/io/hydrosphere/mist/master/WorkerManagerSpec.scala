package io.hydrosphere.mist.master

import akka.actor._
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import com.typesafe.config.ConfigFactory
import io.hydrosphere.mist.Messages.JobMessages._
import io.hydrosphere.mist.Messages.WorkerMessages._
import io.hydrosphere.mist.jobs.Action
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{FunSpecLike, Matchers}

import scala.concurrent.duration._
import scala.util.Success
import WorkerManagerSpec._

class WorkerManagerSpec extends TestKit(ActorSystem(systemName, config))
  with ImplicitSender
  with FunSpecLike
  with Matchers
  with Eventually {

  val NothingRunner = new WorkerRunner {
    override def run(settings: WorkerSettings): Unit = {}
  }

  val StatusService = TestProbe().ref

  implicit override val patienceConfig =
    PatienceConfig(timeout = scaled(Span(2, Seconds)), interval = scaled(Span(5, Millis)))

  it("should connect frond and back") {
    val manager = system.actorOf(WorkersManager.props(StatusService, NothingRunner))

    val params = JobParams("path", "MyClass", Map.empty, Action.Execute)
    manager ! WorkerCommand("test", RunJobRequest("id", params))

    val info = receiveOne(1.second).asInstanceOf[ExecutionInfo]
    info.request.id shouldBe "id"
    info.promise.future.isCompleted shouldBe false

    // create fixture for backend worker
    system.actorOf(Props(classOf[WorkerFixture]), "worker-test")
    manager ! WorkerRegistration("test", Address("akka.tcp", systemName, "127.0.0.1", 2554))

    eventually(timeout(Span(5, Seconds))) {
      info.promise.future.isCompleted shouldBe true
      info.promise.future.value shouldBe Some(Success(Map("r" -> "ok")))
    }
  }

  it("should return active workers") {
    val manager = system.actorOf(WorkersManager.props(StatusService, NothingRunner))

    manager ! GetWorkers
    expectMsg(List.empty[String])

    system.actorOf(Props(classOf[WorkerFixture]), "worker-test1")
    system.actorOf(Props(classOf[WorkerFixture]), "worker-test2")

    manager ! WorkerRegistration("test1", Address("akka.tcp", systemName, "127.0.0.1", 2554))
    manager ! WorkerRegistration("test2", Address("akka.tcp", systemName, "127.0.0.1", 2554))

    eventually(timeout(Span(5, Seconds))) {
      manager ! GetWorkers
      expectMsgPF(){
        case workers: List[_] =>
          workers should contain allOf(
            WorkerLink("test1", s"akka.tcp://$systemName@127.0.0.1:2554"),
            WorkerLink("test2", s"akka.tcp://$systemName@127.0.0.1:2554"))
      }
    }
  }

}

class WorkerFixture extends Actor {

  override def receive: Actor.Receive = {
    case r @ RunJobRequest(id, params) =>
      sender() ! JobStarted(id)
      sender() ! JobSuccess(id, Map("r" -> "ok"))
  }
}

object WorkerManagerSpec {

  val systemName = "TestWorkersManager"

  val config = ConfigFactory.parseString(
    s"""
       |akka {
       |  loggers = ["akka.event.slf4j.Slf4jLogger"]
       |  logging-filter = "akka.event.slf4j.Slf4jLoggingFilter"
       |  actor.provider = "akka.cluster.ClusterActorRefProvider"
       |
       |  remote {
       |    log-remote-lifecycle-events = off
       |    log-recieved-messages = off
       |    netty.tcp {
       |      hostname = "127.0.0.1"
       |      port = 2554
       |    }
       |    transport-failure-detector {
       |      heartbeat-interval = 30s
       |      acceptable-heartbeat-pause = 5s
       |    }
       |  }
       |
       |  cluster {
       |    seed-nodes = ["akka.tcp://$systemName@127.0.0.1:2554"]
       |    auto-down-unreachable-after = 10s
       |  }
       |}
     """.stripMargin

  )
}
