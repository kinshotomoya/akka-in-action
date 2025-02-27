package com.goticks

import scala.concurrent.Future

import akka.actor._
import akka.util.Timeout

object BoxOffice {
  def props(implicit timeout: Timeout) = Props(new BoxOffice)
  def name = "boxOffice"

  // 送信するmessageをcase classで持っておく
  case class CreateEvent(name: String, tickets: Int)
  case class GetEvent(name: String)
  case object GetEvents
  case class GetTickets(event: String, tickets: Int)
  case class CancelEvent(name: String)

  case class Event(name: String, tickets: Int)
  case class Events(events: Vector[Event])

  case class UpdateEvent(event: String)
  case class EventUpdated(event: Event)

  sealed trait EventResponse
  case class EventCreated(event: Event) extends EventResponse
  case object EventExists extends EventResponse


}

class BoxOffice(implicit timeout: Timeout) extends Actor {
  import BoxOffice._
  import context._


  def createTicketSeller(name: String) =
    context.actorOf(TicketSeller.props(name), name)

  def receive = {
    case CreateEvent(name, tickets) =>
      def create() = {
        val eventTickets = createTicketSeller(name)
        // Actor[akka://default/user/boxOffice/arashi#1687850722]
        // ticketSeller actorを作成
        println(eventTickets)
        val newTickets = (1 to tickets).map { ticketId =>
          TicketSeller.Ticket(ticketId)
        }.toVector
        println(newTickets)
        // eventTickets(actor) !(send) TicketSeller.Add(newTickets) (message)
        // case Add(newTickets) => tickets = tickets ++ newTickets ここに処理が行く
        eventTickets ! TicketSeller.Add(newTickets)
        // receiveメソッドにmessageを送ってきた箇所に、responseを返している
        sender() ! EventCreated(Event(name, tickets))
      }
      context.child(name).fold(create())(_ => sender() ! EventExists)



    case GetTickets(event, tickets) =>
      def notFound() = sender() ! TicketSeller.Tickets(event)
      def buy(child: ActorRef) =
        child.forward(TicketSeller.Buy(tickets))

      context.child(event).fold(notFound())(buy)


    case GetEvent(event) =>
      def notFound() = sender() ! None
      def getEvent(child: ActorRef) = child forward TicketSeller.GetEvent
      context.child(event).fold(notFound())(getEvent)


    case GetEvents =>
      import akka.pattern.ask
      import akka.pattern.pipe

      def getEvents = context.children.map { child =>
        self.ask(GetEvent(child.path.name)).mapTo[Option[Event]]
      }
      def convertToEvents(f: Future[Iterable[Option[Event]]]) =
        f.map(_.flatten).map(l=> Events(l.toVector))

      pipe(convertToEvents(Future.sequence(getEvents))) to sender()


    case CancelEvent(event) =>
      def notFound() = sender() ! None
      def cancelEvent(child: ActorRef) = child forward TicketSeller.Cancel
      context.child(event).fold(notFound())(cancelEvent)

    // messageがこの型ならば
    case UpdateEvent(event) =>
      println(event)
      // ticketSeller actorを作成する
      val eventTickets = createTicketSeller(event)
      // ticketSeller actorに、Updateメッセージを送る
      eventTickets ! TicketSeller.Update(event, 1)
      sender() ! EventUpdated(Event(event, 1))
  }
}

