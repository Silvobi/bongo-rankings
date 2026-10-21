package pl.bongo.rankings;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.core.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraft.network.chat.Component;

/** Only packaged into the isolated test mod, never the release jar. */
public final class TestProbe implements ModInitializer {
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((d,r,e)->d.register(Commands.literal("rankingprobe").executes(c->{
            var p=c.getSource().getPlayerOrException();
            p.setExperienceLevels(0);p.setExperiencePoints(0);
            p.giveExperiencePoints(1395);p.giveExperienceLevels(1);
            p.onEnchantmentPerformed(new ItemStack(Items.DIAMOND_PICKAXE),1);p.giveExperienceLevels(-1);
            var pos=new BlockPos(0,100,0);var level=p.level();
            level.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
            p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STONE,64));
            var hit=new BlockHitResult(new Vec3(0.5,100,0.5),Direction.UP,pos.below(),false);
            var context=new BlockPlaceContext(p,InteractionHand.MAIN_HAND,p.getMainHandItem(),hit);
            if (!((BlockItem)Items.STONE).place(context).consumesAction()) throw new IllegalStateException("Probe placement failed");
            var bad=new BlockHitResult(new Vec3(0,500,0),Direction.UP,new BlockPos(0,500,0),false);
            if (((BlockItem)Items.STONE).place(new BlockPlaceContext(p,InteractionHand.MAIN_HAND,p.getMainHandItem(),bad)).consumesAction())
                throw new IllegalStateException("Out-of-height placement unexpectedly succeeded");
            p.sendSystemMessage(Component.literal("PROBE_OK"));return 1;
        })));
    }
}
