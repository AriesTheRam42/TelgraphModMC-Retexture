package com.reis.telegraph.entities;

import com.reis.telegraph.registration.ModBlocks;
import com.reis.telegraph.registration.ModEntities;
import com.reis.telegraph.registration.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

public class DynamiteEntity extends ThrowableItemProjectile {
    private static final float EXPLOSION_POWER = 1.35F;
    private static final int CABLE_BREAK_RADIUS = 2;

    public DynamiteEntity(EntityType<? extends DynamiteEntity> entityType, Level level) {
        super(entityType, level);
    }

    public DynamiteEntity(Level level, LivingEntity thrower) {
        super(ModEntities.DYNAMITE.get(), thrower, level);
    }

    public DynamiteEntity(Level level, double x, double y, double z) {
        super(ModEntities.DYNAMITE.get(), x, y, z, level);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.DYNAMITE.get();
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);

        if (!this.level().isClientSide) {
            sabotageExplosion();
        }
    }

    private void sabotageExplosion() {
        BlockPos center = this.blockPosition();
        Level level = this.level();
        double maxDistanceSq = (CABLE_BREAK_RADIUS + 0.5D) * (CABLE_BREAK_RADIUS + 0.5D);

        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-CABLE_BREAK_RADIUS, -CABLE_BREAK_RADIUS, -CABLE_BREAK_RADIUS),
                center.offset(CABLE_BREAK_RADIUS, CABLE_BREAK_RADIUS, CABLE_BREAK_RADIUS))) {
            if (pos.distSqr(center) <= maxDistanceSq && level.getBlockState(pos).is(ModBlocks.CABLE_BLOCK.get())) {
                level.destroyBlock(pos, false);
            }
        }

        level.explode(this, this.getX(), this.getY(), this.getZ(), EXPLOSION_POWER, Level.ExplosionInteraction.BLOCK);
        this.discard();
    }
}
