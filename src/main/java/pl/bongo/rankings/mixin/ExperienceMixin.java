package pl.bongo.rankings.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.bongo.rankings.*;

@Mixin(Player.class)
public abstract class ExperienceMixin {
    @Unique private int rankings$pointDepth;
    @Unique private long rankings$beforeLevels, rankings$beforeEnchant;
    @Unique private long rankings$balance() {
        Player p = (Player)(Object)this;
        return Formats.xp(p.experienceLevel, p.experienceProgress);
    }
    @Unique private void rankings$record(long delta) {
        if ((Object)this instanceof ServerPlayer p && BongoRankings.runtime != null && delta != 0)
            BongoRankings.runtime.add(p, delta > 0 ? Metric.XP_GAINED : Metric.XP_SPENT, Math.abs(delta));
    }
    @Inject(method = "giveExperiencePoints", at = @At("HEAD"))
    private void rankings$pointsStart(int points, CallbackInfo ci) {
        rankings$pointDepth++;
        // Awarded points are cumulative even if vanilla's totalExperience reaches its int limit.
        if (points > 0) rankings$record(points);
        else if (points < 0) rankings$record(-Math.min(-(long)points, rankings$balance()));
    }
    @Inject(method = "giveExperiencePoints", at = @At("RETURN"))
    private void rankings$pointsEnd(int points, CallbackInfo ci) { rankings$pointDepth--; }
    @Inject(method = "giveExperienceLevels", at = @At("HEAD"))
    private void rankings$levelsStart(int levels, CallbackInfo ci) { rankings$beforeLevels = rankings$balance(); }
    @Inject(method = "giveExperienceLevels", at = @At("RETURN"))
    private void rankings$levelsEnd(int levels, CallbackInfo ci) {
        if (rankings$pointDepth == 0) rankings$record(rankings$balance() - rankings$beforeLevels);
    }
    @Inject(method = "onEnchantmentPerformed", at = @At("HEAD"))
    private void rankings$enchantStart(ItemStack stack, int levels, CallbackInfo ci) { rankings$beforeEnchant = rankings$balance(); }
    @Inject(method = "onEnchantmentPerformed", at = @At("RETURN"))
    private void rankings$enchantEnd(ItemStack stack, int levels, CallbackInfo ci) { rankings$record(rankings$balance() - rankings$beforeEnchant); }
}
