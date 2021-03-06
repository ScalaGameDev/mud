package mud.session

import chan.ServerChannelWorld._
import mud._
import chan.ServerChannelState

case class Registration(d: Dungeon) extends ServerChannelState {

  def prompt: Action[Unit] =
    write(s"What is your name? ")

  def input(s: String): Action[ServerChannelState] =
    if (s.trim.isEmpty)
      kick("Ok, nevermind.").map(_ => ServerChannelState.Closed)
    else
      for {
        // TODO: there is a race here; player introduction needs to be atomic on `d`
        b <- d.playerExists(s).liftIO[Action]  
        s <- if (b) tryAgain(s) else create(s)
      } yield s

  def tryAgain(s: String): Action[ServerChannelState] =
    writeLn(s"Hmm, $s is already playing. Try again.").map(_ => this)

  def create(s: String): Action[ServerChannelState] =
    for {
      m <- unit(Player(s))
      w <- writer
      _ <- d.setAvatar(m, new DefaultTextAvatar(m, w)).liftIO[Action]
      _ <- d.intro(m).liftIO[Action]
      r <- remoteAddress
      _ <- Log.info(s"Registered: $r as ${m.name}").liftIO[Action]
    } yield Playing(d, m)

  def closed: Action[Unit] =
    unit(())

}


