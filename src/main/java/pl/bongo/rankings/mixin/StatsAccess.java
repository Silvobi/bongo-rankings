package pl.bongo.rankings.mixin;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.stats.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(StatsCounter.class)
public interface StatsAccess {
    @Accessor("stats") Object2IntMap<Stat<?>> rankings$stats();
}
