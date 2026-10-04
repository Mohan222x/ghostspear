package com.example.ghostspear.client.mixin;

import com.example.ghostspear.client.GhostState;
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {
    @Inject(method = "sendPacket", at = @At("HEAD"), cancellable = true)
    private void ghost$blockMovement(Packet<?> packet, CallbackInfo ci) {
        if (GhostState.desynced && packet instanceof PlayerMoveC2SPacket) {
            ci.cancel();
        }
    }
}
