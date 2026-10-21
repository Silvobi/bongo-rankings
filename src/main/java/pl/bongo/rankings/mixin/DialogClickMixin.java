package pl.bongo.rankings.mixin;

import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.server.network.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.bongo.rankings.BongoRankings;

@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class DialogClickMixin {
    @Inject(method = "handleCustomClickAction", at = @At("RETURN"))
    private void rankings$click(ServerboundCustomClickActionPacket packet, CallbackInfo ci) {
        if (packet.id().getNamespace().equals(BongoRankings.ID) && (Object)this instanceof ServerGamePacketListenerImpl game
                && BongoRankings.runtime != null) BongoRankings.runtime.click(game.player, packet.id().getPath());
    }
}
