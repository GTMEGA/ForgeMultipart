package codechicken.multipart.minecraft;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.network.play.INetHandlerPlayClient;

import codechicken.lib.packet.PacketCustom;
import codechicken.lib.packet.PacketCustom.IClientPacketHandler;
import codechicken.lib.vec.BlockCoord;

public class McMultipartCPH implements IClientPacketHandler {

    public static Object channel = MinecraftMultipartMod.instance;

    @Override
    public void handlePacket(PacketCustom packet, Minecraft mc, INetHandlerPlayClient netHandler) {
        switch (packet.getType()) {
            case 1:
                spawnBurnoutSmoke(mc.theWorld, packet.readCoord());
                break;
        }
    }

    private void spawnBurnoutSmoke(WorldClient world, BlockCoord pos) {
        for (int l = 0; l < 5; l++) world.spawnParticle(
                "smoke",
                pos.x + world.rand.nextDouble() * 0.6 + 0.2,
                pos.y + world.rand.nextDouble() * 0.6 + 0.2,
                pos.z + world.rand.nextDouble() * 0.6 + 0.2,
                0,
                0,
                0);
    }
}
