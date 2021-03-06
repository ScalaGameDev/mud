package mud

import scala.concurrent.stm._
import util.M2O
import util.World
import scalaz.effect.IO
import scalaz._
import Scalaz._

// Our mutable state is hidden in an effect world whose actions compose transactionally and can be run only in IO.
final class GameState(map: Map[Room, Map[Direction, Door]], ms: M2O[Mobile, Room]) extends World {

  // Find limbo and our starting room; we need them. Diverge on failure.
  val Limbo = map.keys.find(_.name == "Limbo").getOrElse(sys.error("Fatal: can't find Limbo"))
  val Start = map.keys.find(_.name == "The Grunting Boar").getOrElse(sys.error("Fatal: can't find The Grunting Boar"))

  // Actions pass a transaction as their state
  protected type State = InTxn

  // But our world also encapsulates mutable state
  private val portals: Ref[Map[Room, Map[Direction, Door]]] = Ref(map)
  private val mobiles: Ref[M2O[Mobile, Room]] = Ref(ms)
  private val items:   Ref[M2O[Item, Room]] = Ref(M2O.empty)
  private val avatars: Ref[Map[Mobile,Avatar]] = Ref(Map())
  
  // And because our actions can modify mutable state, they can only be run in IO
  implicit class RunnableAction[A](a: Action[A]) {
    def run: IO[A] = IO(atomic(runWorld(a, _)._2))
  }

  // Primitive actions
  def allAvatars: Action[Map[Mobile,Avatar]] =
    effect { implicit tx => avatars() }

  // TODO: notify old avatar, if any
  def attachAvatar(m:Mobile, a:Avatar): Action[Unit] = 
    effect { implicit tx => avatars() = avatars() + (m -> a)}
  
  def avatar(m:Mobile):Action[Option[Avatar]] =
    effect { implicit tx => avatars().get(m) }
  
  def findRoom(s: String): Action[Option[Room]] =
    effect { implicit t => portals().keys.find(_.name equalsIgnoreCase s) }

  def findMobile(m: Mobile): Action[Room] =
    effect { implicit t => mobiles().left(m).getOrElse(Limbo) }

  def lookupMobile(s:String): Action[Option[Mobile]] =
    effect { implicit t => mobiles().o2m.ba.keySet.find(_.name equalsIgnoreCase s) }

  def mobilesInRoom(r: Room): Action[Set[Mobile]] =
    effect { implicit t => 
      val ms = mobiles().right(r) 
      println("mobiles in " + r.name + " => " + ms.map(_.name))
      ms
    }

  def move(m: Mobile, dest: Room): Action[Unit] =
    effect { implicit t => 
      println("moving " + m.name + " to " + dest.name)
      mobiles() = mobiles() + (m -> dest) 
    }
  
  // What about this?
  val tryAgain: Action[Nothing] =
    effect { implicit t => retry }
  
  // TODO: this is awkward
  def removeMobile(m:Mobile): Action[Unit] =
    for {
      r <- findMobile(m)
      _ <- effect { implicit t => mobiles() = mobiles() - (m -> r) }
      _ <- effect { implicit t => avatars() = avatars() - m }
    } yield ()    
  
  def portals(r: Room): Action[Map[Direction, Door]] =
    effect { implicit t => portals().get(r).getOrElse(Map()) }

  def unit[A](a: A): Action[A] =
    super.unit(a)

  // Derived actions

  case class RoomInfo(room: Room, mobiles: Set[Mobile], portals: Map[Direction, Door])

  def roomInfo(room: Room): Action[RoomInfo] =
    (mobilesInRoom(room) |@| portals(room))(RoomInfo(room, _, _))

  def roomInfo(mobile: Mobile): Action[RoomInfo] =
    findMobile(mobile) >>= roomInfo

  def tryMove(m: Mobile, d: Direction): Action[Option[(RoomInfo, RoomInfo)]] =
    for {
      r <- findMobile(m)
      i <- portals(r).map(_.get(d)) >>= {

        // No such exit
        case None =>
          unit(None)

        // The exit is legal
        case Some(p) =>
          for {
            _ <- move(m, p.dest)
            a <- roomInfo(r)
            b <- roomInfo(p.dest)
          } yield Some((a, b))

      }
    } yield i

}