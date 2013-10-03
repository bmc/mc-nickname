package org.clapper.minecraft.nickname

import com.joshcough.minecraft.{ScalaPlugin, CommandPlugin, ListenersPlugin}
import com.joshcough.minecraft.Listeners._

import org.bukkit.event.{EventHandler, Listener}
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.entity.Player
import org.bukkit.metadata.MetadataValue

import org.clapper.minecraft.lib.{PluginLogging, Logging}

import scala.language.implicitConversions
import scala.collection.JavaConverters._
import scala.collection.mutable.{Map => MutableMap}

import java.io._
import scala.util.{Failure, Success, Try}
import scala.util.Failure
import scala.Some
import scala.util.Success
import java.util.logging.Logger
import org.bukkit.scheduler.BukkitRunnable

private class EnrichedPlayer(val player: Player) {
  def notice(message: String) = {
    player.sendRawMessage(s"§e${message}")
  }
}

private object Implicits {
  implicit def playerToEnrichedPlayer(p: Player) = new EnrichedPlayer(p)
  implicit def enrichedPlayerToPlayer(e: EnrichedPlayer) = e.player
}

object NicknameConstants {
  val CanChangePerm     = "nickname.canchange"
  val DataFile          = "nicknames.dat"

  // There are 20 Minecraft ticks per second. See
  // http://minecraft.gamepedia.com/Tick
  val TicksPerSecond = 20
  val TicksPerMinute = 20 * 60

  val SchedulerInterval = 5 * TicksPerMinute
}

class NicknameData(dataFolder: File, val logger: Logger) extends Logging {

  // Map of playerName -> nickname values.
  private val nicknameMap = MutableMap.empty[String, String]
  private val DataFile    = new File(dataFolder, NicknameConstants.DataFile)

  def nicknameFor(player: Player): Option[String] = nicknameMap.get(player.name)

  def saveNickname(player: Player, nickname: String): Unit = {
    nicknameMap += player.name -> nickname
  }

  def removeNickname(player: Player): Unit = {
    nicknameMap -= player.name
  }

  def save(): Unit = {
    def doSave(): Try[Boolean] = {
      Try {
        logDebug(s"Saving nickname data to ${DataFile.getPath}")
        val out = new ObjectOutputStream(new FileOutputStream(DataFile))
        out.writeObject(nicknameMap)
        out.close()
        true
      }
    }

    doSave() match {
      case Success(ok) => logMessage("Saved nickname data.")
      case Failure(ex) => logError(s"Failed to save nickname data: ${ex}")
    }
  }

  def load(): Unit = {
    def doLoad(): Try[Boolean] = {
      Try {
        if (DataFile.exists) {
          logDebug(s"Reading nicknames from ${DataFile.getPath}")
          val in = new ObjectInputStream(new FileInputStream(DataFile))
          val map = in.readObject().asInstanceOf[MutableMap[String, String]]
          in.close()
          nicknameMap.clear()
          nicknameMap ++= map
        }
        true
      }
    }

    doLoad() match {
      case Success(ok) => logMessage("Loaded nickname data.")
      case Failure(ex) => logError(s"Failed to load nickname data: ${ex}")
    }
  }
}

trait NicknamePermissions extends Logging {
  self: ScalaPlugin =>

  def ifPermittedToChangeName(player: Player)(code: => Unit) = {
    if ((! player.isPermissionSet(NicknameConstants.CanChangePerm)) ||
        player.hasPermission(NicknameConstants.CanChangePerm)) {
      code
    }

    else {
      logMessage(s"Player ${player.name} isn't permitted to change nicknames.")
      player.sendError("You aren't permitted to change your nickname.")
    }
  }
}

class NicknamePlugin
  extends ListenersPlugin
  with    CommandPlugin
  with    PluginLogging
  with    NicknamePermissions {

  private lazy val nicknameData = new NicknameData(this.getDataFolder, logger)

  import Implicits._

  val listeners = List(
    OnPlayerJoin { (player, event) =>
      logMessage(s"${player.name} logged in.")
      nicknameData.nicknameFor(player) match {
        case None =>
          logMessage(s"${player.name} has no saved nickname.")

        case Some(nickname) => {
          logMessage(s"${player.name} has saved nickname: $nickname")
          setName(player, Some(nickname))
        }
      }
    },

    new Listener {
      @EventHandler def on(e: AsyncPlayerChatEvent): Unit = {
        val player = e.getPlayer
        // §r<%1$s> %2$s
        val format = e.getFormat
        val newFormat = if (format.indexOf("%1$s") >= 0) {
          format.replace("%1$s", player.getPlayerListName)
        }
        else {
          " <" + player.getPlayerListName + "> %2$s"
        }
        e.setFormat(newFormat)
      }
    }
  )

  val command = Command("nk", "Change or show your nickname.", nothing or slurp) {
    case (player, Left(_)) =>
      ifPermittedToChangeName(player) {
        player.notice(s"Your current nickname is: ${getName(player)}")
      }

    case (player, Right(name)) =>
      ifPermittedToChangeName(player) {
        val trimmedName = name.trim

        if (trimmedName == "-") {
          setName(player, None)
          player.notice("Your nickname has been cleared.")
        }

        else {
          setName(player, Some(trimmedName))
          player.notice(s"Your nickname is now: $trimmedName")
        }
      }
  }

  override def onEnable(): Unit = {
    super.onEnable()
    val dataFolder = this.getDataFolder
    if (! dataFolder.exists) {
      logMessage(s"Creating ${dataFolder}")
      dataFolder.mkdirs()
    }

    nicknameData.load()
    DataCheckpointTask.initialize(this, this.nicknameData, this.logger)
  }

  override def onDisable: Unit = {
    super.onDisable()
    nicknameData.save()
  }

  private def setName(player: Player, nameOpt: Option[String]): Unit = {
    nameOpt match {
      case None => {
        player.setDisplayName(null)
        player.setPlayerListName(null)
        player.setCustomName(null)
        nicknameData.removeNickname(player)
      }

      case Some(name) => {
        player.setDisplayName(name)
        player.setPlayerListName(name)
        player.setCustomName(name)
        nicknameData.saveNickname(player, name)
      }
    }

    nicknameData.save()
  }

  private def getName(player: Player) = player.getDisplayName
}

class DataCheckpointTask(data: NicknameData, val logger: Logger)
  extends BukkitRunnable
  with Logging {

  def run: Unit = {
    data.save()
  }
}

object DataCheckpointTask {
  def initialize(plugin: ScalaPlugin, data: NicknameData, logger: Logger) = {
    import NicknameConstants.SchedulerInterval

    logger.info("Spawning checkpoint task.")
    new DataCheckpointTask(data, logger).runTaskTimer(plugin,
                                                      0,
                                                      SchedulerInterval)
  }
}
