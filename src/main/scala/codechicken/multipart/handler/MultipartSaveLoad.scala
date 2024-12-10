package codechicken.multipart.handler

import net.minecraft.tileentity.TileEntity
import net.minecraft.nbt.NBTTagCompound

import java.util.{Collections, Map}
import net.minecraft.world.chunk.Chunk
import net.minecraft.world.ChunkPosition
import codechicken.multipart.{MultipartHelper, TileMultipart}
import codechicken.lib.asm.ObfMapping
import codechicken.multipart
import net.minecraft.world.World

import scala.collection.mutable
import codechicken.multipart.MultipartHelper.IPartTileConverter

import scala.collection.JavaConversions._

/** Hack due to lack of TileEntityLoadEvent in forge
  */
object MultipartSaveLoad {
  val converters = mutable.MutableList[IPartTileConverter[_]]()
  var loadingWorld: World = _

  class TileNBTContainer extends TileEntity {
    var ticks = 0
    var failed = false
    var loaded = false
    var tag: NBTTagCompound = _

    override def readFromNBT(t: NBTTagCompound) {
      super.readFromNBT(t)
      tag = t
    }

    override def writeToNBT(t : NBTTagCompound) {
      if (tag != null) {
        val keys = tag.func_150296_c.asInstanceOf[Set[String]]
        for (key: String <- keys) {
          t.setTag(key, tag.getTag(key).copy())
        }
      }
      super.writeToNBT(t)
    }

    override def updateEntity(): Unit = {
      if (failed || loaded) {
        return
      }

      if (tag == null) {
        if (ticks >= 600) {
          failed = true
          multipart.logger.warn(s"SavedMultipart at ($xCoord, $yCoord, $zCoord) still exists after $ticks!")
          worldObj.removeTileEntity(xCoord, yCoord, zCoord)
        }
        ticks += 1
        return
      }

      if (worldObj.isRemote)
        return

      val newTile = TileMultipart.createFromNBT(tag)
      if (newTile == null) {
        worldObj.removeTileEntity(xCoord, yCoord, zCoord)
        return
      }

      newTile.validate()
      worldObj.setTileEntity(xCoord, yCoord, zCoord, newTile)
      newTile.notifyTileChange()
      val chunk = worldObj.getChunkFromBlockCoords(xCoord, zCoord)
      val packet = MultipartSPH.getDescPacket(chunk, Collections.singleton[TileEntity](newTile).iterator)
      packet.sendToChunk(worldObj, chunk.xPosition, chunk.zPosition)
      loaded = true
    }
  }

  def hookLoader() {
    val field = classOf[TileEntity].getDeclaredField(
      new ObfMapping(
        "net/minecraft/tileentity/TileEntity",
        "field_145855_i",
        "Ljava/util/Map;"
      ).toRuntime.s_name
    )
    field.setAccessible(true)
    val map = field.get(null).asInstanceOf[Map[String, Class[_ <: TileEntity]]]
    map.put("savedMultipart", classOf[TileNBTContainer])
  }

  private val classToNameMap = getClassToNameMap

  def registerTileClass(t: Class[_ <: TileEntity]) {
    classToNameMap.put(t, "savedMultipart")
  }

  def getClassToNameMap = {
    val field = classOf[TileEntity].getDeclaredField(
      new ObfMapping(
        "net/minecraft/tileentity/TileEntity",
        "field_145853_j",
        "Ljava/util/Map;"
      ).toRuntime.s_name
    )
    field.setAccessible(true)
    field.get(null).asInstanceOf[Map[Class[_ <: TileEntity], String]]
  }

  def loadTiles(chunk: Chunk) {
    loadingWorld = chunk.worldObj
    val iterator = chunk.chunkTileEntityMap
      .asInstanceOf[Map[ChunkPosition, TileEntity]]
      .entrySet
      .iterator
    while (iterator.hasNext) {
      val e = iterator.next
      var next = false
      val t = e.getValue match {
        case t: TileNBTContainer if t.tag.getString("id") == "savedMultipart" =>
          TileMultipart.createFromNBT(
            e.getValue.asInstanceOf[TileNBTContainer].tag
          )
        case t =>
          converters.find(_.canConvert(t)) match {
            case Some(c) =>
              val parts = c.convert(t)
              if (!parts.isEmpty)
                MultipartHelper.createTileFromParts(parts)
              else
                null
            case _ =>
              next = true
              null
          }
      }

      if (!next) {
        if (t != null) {
          t.setWorldObj(e.getValue.getWorldObj)
          t.validate()
          e.setValue(t)
        } else
          iterator.remove()
      }
    }
    loadingWorld = null
  }
}
