package com.heytozzz.htzdistance.render;

//? if >=1.21.10 {
/*import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.ItemFrameRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemDisplayContext;
*///?}

/**
 * Re-resolves the item parts of a reused ghost render state every frame (1.21.10+).
 *
 * <p>Render states are meant to be drawn once. Some mods attach per-frame data to the item
 * render states while items are resolved and clear it right after drawing (MyTotemDoll does this
 * to swap totems for dolls), so a reused state would show the plain item on every frame but the
 * first. Resolving the items again each frame keeps those mods working while the rest of the
 * state is still reused.
 */
final class ItemStateRefresh {
    private ItemStateRefresh() {
    }

    //? if >=1.21.10 {
    /*static void refresh(EntityRenderState state, Entity entity, float partialTick) {
        ItemModelResolver resolver = Minecraft.getInstance().getItemModelResolver();
        if (state instanceof ItemFrameRenderState frameState && entity instanceof ItemFrame frame) {
            resolver.updateForNonLiving(frameState.item, frame.getItem(), ItemDisplayContext.FIXED, frame);
        } else if (state instanceof ArmedEntityRenderState armed && entity instanceof LivingEntity living) {
            refreshHands(living, armed, resolver, partialTick);
            // Only refresh the head item when vanilla used it (blocks/items worn on the head).
            if (!armed.headItem.isEmpty()) {
                resolver.updateForLiving(armed.headItem, living.getItemBySlot(EquipmentSlot.HEAD), ItemDisplayContext.HEAD, living);
            }
        }
    }
    *///?}

    //? if >=1.21.11 {
    /*private static void refreshHands(LivingEntity living, ArmedEntityRenderState state, ItemModelResolver resolver, float partialTick) {
        ArmedEntityRenderState.extractArmedEntityRenderState(living, state, resolver, partialTick);
    }
    *///?} else if >=1.21.10 {
    /*private static void refreshHands(LivingEntity living, ArmedEntityRenderState state, ItemModelResolver resolver, float partialTick) {
        ArmedEntityRenderState.extractArmedEntityRenderState(living, state, resolver);
    }
    *///?}
}
