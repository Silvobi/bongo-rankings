package pl.bongo.rankings.mixin;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.InteractionResult;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.bongo.rankings.*;

@Mixin(BlockItem.class)
public abstract class BlockPlaceMixin {
    @Inject(method = "place", at = @At("RETURN"))
    private void rankings$place(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> ci) {
        if (ci.getReturnValue().consumesAction() && context.getPlayer() instanceof ServerPlayer player && BongoRankings.runtime != null)
            BongoRankings.runtime.add(player, Metric.PLACED, 1);
    }
}
