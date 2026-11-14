package com.heytozzz.htzdistance.render;

//? if >=1.21.10 {
/*import com.heytozzz.htzdistance.HtzDistanceClient;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
*///?}

/**
 * Keeps the item parts of a reused ghost render state intact across frames (1.21.10+).
 *
 * <p>Render states are meant to be drawn once. Some mods store per-frame data in the item render
 * states while items are resolved and clear it right after drawing (MyTotemDoll does this to
 * swap totems for dolls), so a reused state would show the plain item on every frame but the
 * first. Instead of resolving every item again each frame (expensive: about 20 microseconds per
 * item), a shallow copy of every {@code ItemStackRenderState} of the state is taken right after
 * extraction, fields added by mixins included, and written back before each frame.
 */
final class ItemStateSnapshot {
    //? if >=1.21.10 {
    /*/^* Instance fields of ItemStackRenderState, including the ones mods add with mixins. ^/
    @Nullable
    private static final Field[] ITEM_FIELDS = itemFields();
    /^* ItemStackRenderState fields of each entity render state class. ^/
    private static final ClassValue<Field[]> STATE_FIELDS = new ClassValue<>() {
        @Override
        protected Field[] computeValue(Class<?> type) {
            List<Field> found = new ArrayList<>();
            for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field field : c.getDeclaredFields()) {
                    if (!Modifier.isStatic(field.getModifiers()) && field.getType() == ItemStackRenderState.class) {
                        field.setAccessible(true);
                        found.add(field);
                    }
                }
            }
            return found.toArray(new Field[0]);
        }
    };

    private final ItemStackRenderState[] live;
    private final ItemStackRenderState[] saved;

    private ItemStateSnapshot(ItemStackRenderState[] live, ItemStackRenderState[] saved) {
        this.live = live;
        this.saved = saved;
    }

    /^*
     * Copies the item states of a freshly extracted render state. Returns {@code null} when the
     * copy is not possible; the caller then resolves the items every frame instead.
     ^/
    @Nullable
    static ItemStateSnapshot take(EntityRenderState state) {
        if (ITEM_FIELDS == null) {
            return null;
        }
        try {
            Field[] fields = STATE_FIELDS.get(state.getClass());
            List<ItemStackRenderState> live = new ArrayList<>(fields.length);
            for (Field field : fields) {
                Object value = field.get(state);
                if (value instanceof ItemStackRenderState item && !item.isEmpty()) {
                    live.add(item);
                }
            }
            ItemStackRenderState[] liveArray = live.toArray(new ItemStackRenderState[0]);
            ItemStackRenderState[] saved = new ItemStackRenderState[liveArray.length];
            for (int i = 0; i < liveArray.length; i++) {
                saved[i] = new ItemStackRenderState();
                copy(liveArray[i], saved[i]);
            }
            return new ItemStateSnapshot(liveArray, saved);
        } catch (ReflectiveOperationException | RuntimeException e) {
            HtzDistanceClient.LOGGER.warn("Could not copy ghost item states, items will be resolved every frame", e);
            return null;
        }
    }

    /^* Writes the saved item states back into the live render state. ^/
    void restore() {
        try {
            for (int i = 0; i < live.length; i++) {
                copy(saved[i], live[i]);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not restore ghost item states", e);
        }
    }

    private static void copy(ItemStackRenderState from, ItemStackRenderState to) throws IllegalAccessException {
        for (Field field : ITEM_FIELDS) {
            field.set(to, field.get(from));
        }
    }

    @Nullable
    private static Field[] itemFields() {
        try {
            List<Field> fields = new ArrayList<>();
            for (Class<?> c = ItemStackRenderState.class; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field field : c.getDeclaredFields()) {
                    // Final fields cannot be cleared by anyone, so there is nothing to restore.
                    if (!Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())) {
                        field.setAccessible(true);
                        fields.add(field);
                    }
                }
            }
            return fields.toArray(new Field[0]);
        } catch (RuntimeException e) {
            HtzDistanceClient.LOGGER.warn("Ghost item states cannot be copied, items will be resolved every frame", e);
            return null;
        }
    }
    *///?}
}
