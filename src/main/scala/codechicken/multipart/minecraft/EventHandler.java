package codechicken.multipart.minecraft;

import net.minecraft.block.Block;
import net.minecraft.block.BlockFence;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerDestroyItemEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.Action;

import codechicken.lib.packet.PacketCustom;
import codechicken.lib.raytracer.RayTracer;
import codechicken.lib.vec.BlockCoord;
import codechicken.lib.vec.Vector3;
import codechicken.multipart.TileMultipart;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class EventHandler {

    private ThreadLocal<Object> placing = new ThreadLocal<Object>();

    @SubscribeEvent(priority = EventPriority.LOW)
    public void playerInteract(PlayerInteractEvent event) {
        if (event.action == Action.RIGHT_CLICK_BLOCK && event.entityPlayer.worldObj.isRemote) {
            if (placing.get() != null) return; // for mods that do dumb stuff and call this event like MFR
            placing.set(event);
            if (place(event.entityPlayer, event.entityPlayer.worldObj)) event.setCanceled(true);
            placing.set(null);
        }
    }

    public static boolean place(EntityPlayer player, World world) {
        MovingObjectPosition hit = RayTracer.reTrace(world, player);
        if (hit == null) return false;

        BlockCoord pos = new BlockCoord(hit.blockX, hit.blockY, hit.blockZ).offset(hit.sideHit);
        ItemStack held = player.getHeldItem();
        McBlockPart part = null;
        if (held == null) return false;

        Block heldBlock = Block.getBlockFromItem(held.getItem());
        if (heldBlock == Blocks.air) return false;

        if (heldBlock == Blocks.torch) part = TorchPart.placement(world, pos, hit.sideHit);
        else if (heldBlock == Blocks.lever) part = LeverPart.placement(world, pos, player, hit.sideHit);
        else if (heldBlock == Blocks.stone_button) part = ButtonPart.placement(world, pos, hit.sideHit, 0);
        else if (heldBlock == Blocks.wooden_button) part = ButtonPart.placement(world, pos, hit.sideHit, 1);
        else if (heldBlock == Blocks.redstone_torch) part = RedstoneTorchPart.placement(world, pos, hit.sideHit);

        if (part == null) return false;

        if (world.isRemote && !player.isSneaking()) // attempt to use block activated like normal and tell the server
                                                    // the right stuff
        {
            Vector3 f = new Vector3(hit.hitVec).add(-hit.blockX, -hit.blockY, -hit.blockZ);
            Block block = world.getBlock(hit.blockX, hit.blockY, hit.blockZ);
            if (!ignoreActivate(block) && block.onBlockActivated(
                    world,
                    hit.blockX,
                    hit.blockY,
                    hit.blockZ,
                    player,
                    hit.sideHit,
                    (float) f.x,
                    (float) f.y,
                    (float) f.z)) {
                player.swingItem();
                PacketCustom.sendToServer(
                        new C08PacketPlayerBlockPlacement(
                                hit.blockX,
                                hit.blockY,
                                hit.blockZ,
                                hit.sideHit,
                                player.inventory.getCurrentItem(),
                                (float) f.x,
                                (float) f.y,
                                (float) f.z));
                return true;
            }
        }

        TileMultipart tile = TileMultipart.getOrConvertTile(world, pos);
        if (tile == null || !tile.canAddPart(part)) return false;

        if (!world.isRemote) {
            TileMultipart.addPart(world, pos, part);
            world.playSoundEffect(
                    pos.x + 0.5,
                    pos.y + 0.5,
                    pos.z + 0.5,
                    part.getBlock().stepSound.func_150496_b(),
                    (part.getBlock().stepSound.getVolume() + 1.0F) / 2.0F,
                    part.getBlock().stepSound.getPitch() * 0.8F);
            if (!player.capabilities.isCreativeMode) {
                held.stackSize--;
                if (held.stackSize == 0) {
                    player.inventory.mainInventory[player.inventory.currentItem] = null;
                    MinecraftForge.EVENT_BUS.post(new PlayerDestroyItemEvent(player, held));
                }
            }
        } else {
            player.swingItem();
            new PacketCustom(McMultipartSPH.channel, 1).sendToServer();
        }
        return true;
    }

    /**
     * Because vanilla is weird.
     */
    private static boolean ignoreActivate(Block block) {
        if (block instanceof BlockFence) return true;
        return false;
    }
}
