/* 
 * The MIT License
 *
 * Copyright 2016 Kime.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package me.kime.lumberman;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Optional;
import java.util.Random;
import java.util.function.Function;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.data.Transaction;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.data.manipulator.immutable.block.ImmutableDecayableData;
import org.spongepowered.api.data.meta.ItemEnchantment;
import org.spongepowered.api.effect.sound.SoundTypes;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.entity.EntityTypes;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.block.ChangeBlockEvent;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.event.filter.IsCancelled;
import org.spongepowered.api.event.filter.cause.Root;
import org.spongepowered.api.item.Enchantments;
import org.spongepowered.api.item.ItemType;
import org.spongepowered.api.item.ItemTypes;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.util.Direction;
import org.spongepowered.api.util.Tristate;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

/**
 *
 * @author Kime
 */
public class LMListener {

    private final LumberMan plugin;
    private final Random random;
    private final LinkedHashMap<BlockSnapshot, BlockSnapshot> treeCache;
    private final LinkedList<BlockSnapshot> branchsCache;
    private final LinkedHashMap<BlockSnapshot, BlockSnapshot> leavesCache;

    private final int BRANCH_DEEP = 5;
    private final int LEAVES_DEEP = 1;

    public LMListener(LumberMan plugin) {
        this.plugin = plugin;

        random = new Random();

        treeCache = new LinkedHashMap<>(512);
        branchsCache = new LinkedList<>();
        leavesCache = new LinkedHashMap<>(512);
    }

    @Listener
    @IsCancelled(Tristate.FALSE)
    public void onBlockBreak(ChangeBlockEvent.Break event, @Root Player player) {
        for (Transaction<BlockSnapshot> transaction : event.getTransactions()) {
            BlockSnapshot block = transaction.getOriginal();
            if (isLog(block.getState())) {
                Optional<ItemStack> item = player.getItemInHand();
                if (item.filter(this::isAxe).isPresent() && treeTest(block)) {
                    treeCache.remove(block);
                    popTree(player);
                }
            }
            treeCache.clear();
            branchsCache.clear();
            leavesCache.clear();
        }
    }

    private void popTree(Player player) {
        boolean isBreak = false;
        double healthDamage = 0;

        for (BlockSnapshot b : treeCache.values()) {
            Location<World> location = b.getLocation().get();
            ItemStack drop = ItemStack.builder().fromBlockSnapshot(b).build();
            Optional<Entity> optional = location.getExtent().createEntity(EntityTypes.ITEM, location.getPosition());
            if (optional.isPresent()) {
                Entity item = optional.get();
                item.offer(Keys.REPRESENTED_ITEM, drop.createSnapshot());
                location.getExtent().spawnEntity(item, Cause.of(player));
            }
            b.getLocation().get().removeBlock();
            if (isBreak) {
                healthDamage++;
            } else if (breaksTool(player)) {
                isBreak = true;
            }
        }

        if (healthDamage > 0) {
            Double health = player.get(Keys.HEALTH).get();
            if (health <= healthDamage) {
                health = 0d;
            } else {
                health -= healthDamage;
            }
            player.offer(Keys.HEALTH, health);
        }

        leavesCache.values().forEach(b -> {
            Location<World> location = b.getLocation().get();
            ItemStack drop = ItemStack.builder().fromBlockSnapshot(b).build();
            Optional<Entity> optional = location.getExtent().createEntity(EntityTypes.ITEM, location.getPosition());
            if (optional.isPresent()) {
                Entity item = optional.get();
                item.offer(Keys.REPRESENTED_ITEM, drop.createSnapshot());
                location.getExtent().spawnEntity(item, Cause.of(player));
            }
            b.getLocation().get().removeBlock();
        });

    }

    private boolean treeTest(BlockSnapshot base) {
        treeCache.clear();
        branchsCache.clear();
        leavesCache.clear();
        BlockSnapshot block = base;
        while (isLog(block.getState())) {
            treeCache.put(block, block);

            lookAroundInRange(b -> {
                if (isNatureLeaf(b.getState()) && !leavesCache.containsKey(b)) {
                    leavesCache.put(b, b);
                }
                return false;
            }, block, LEAVES_DEEP);

            //find branch
            lookAround(t -> {
                if (isLog(t.getState())) {
                    branchsCache.addFirst(t);
                }
                return false;
            }, block);

            block = block.getLocation().get().getRelative(Direction.UP).createSnapshot();
        }

        lookAround(t -> {
            if (isLog(t.getState())) {
                branchsCache.addFirst(t);
            }
            return false;
        }, block);

        if ((isNatureLeaf(block.getState())) || (!branchsCache.isEmpty() && branchTest(branchsCache.removeFirst(), 1))) {
            branchsCache.forEach(b -> branchTest(b, 1));
            return true;
        } else {
            return false;
        }

    }

    private boolean branchTest(BlockSnapshot block, final int count) {
        if (!isLog(block.getState())) {
            return false;
        }
        if (count > BRANCH_DEEP) {
            return false;
        }
        if (treeCache.containsKey(block)) {
            return false;
        }

        treeCache.put(block, block);

        lookAroundInRange((BlockSnapshot b) -> {
            if (isNatureLeaf(b.getState()) && !leavesCache.containsKey(b)) {
                leavesCache.put(b, b);
            }
            return false;
        }, block, LEAVES_DEEP);

        boolean isTree = false;

        BlockSnapshot up = block.getLocation().get().getRelative(Direction.UP).createSnapshot();

        isTree = branchTest(up, count + 1) ? true : isTree;
        isTree = lookAround(t -> branchTest(t, count + 1), up) ? true : isTree;
        isTree = lookAround(t -> branchTest(t, count + 1), block) ? true : isTree;

        return isTree || isNatureLeaf(up.getState());
    }

    private boolean breaksTool(Player player) {
        Optional<ItemStack> optional = player.getItemInHand();
        if (optional.isPresent()) {
            ItemStack item = optional.get();
            Optional<Integer> durability = item.get(Keys.ITEM_DURABILITY);
            if (durability.filter(d -> d > 0).isPresent()) {
                Integer durabilityValue = durability.get();
                int unbeakingLevel = 0;
                if (item.get(Keys.ITEM_ENCHANTMENTS).isPresent()) {
                    for (ItemEnchantment e : item.get(Keys.ITEM_ENCHANTMENTS).get()) {
                        if (Enchantments.UNBREAKING.equals(e.getEnchantment())) {
                            unbeakingLevel = e.getLevel();
                            break;
                        }
                    }
                }

                durabilityValue -= (random.nextInt(100) <= (100.0 / (unbeakingLevel + 1))) ? 1 : 0;

                item.offer(Keys.ITEM_DURABILITY, durabilityValue);

                player.setItemInHand(item);

                if (durabilityValue <= 0) {
                    player.setItemInHand(null);
                    player.getWorld().playSound(SoundTypes.ITEM_BREAK, player.getLocation().getPosition(), 1);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean lookAround(Function<BlockSnapshot, Boolean> func, BlockSnapshot block) {
        boolean isTrue = false;
        isTrue = func.apply(block.getLocation().get().getRelative(Direction.EAST).createSnapshot()) ? true : isTrue;
        isTrue = func.apply(block.getLocation().get().getRelative(Direction.SOUTHEAST).createSnapshot()) ? true : isTrue;
        isTrue = func.apply(block.getLocation().get().getRelative(Direction.SOUTH).createSnapshot()) ? true : isTrue;
        isTrue = func.apply(block.getLocation().get().getRelative(Direction.SOUTHWEST).createSnapshot()) ? true : isTrue;
        isTrue = func.apply(block.getLocation().get().getRelative(Direction.WEST).createSnapshot()) ? true : isTrue;
        isTrue = func.apply(block.getLocation().get().getRelative(Direction.NORTHWEST).createSnapshot()) ? true : isTrue;
        isTrue = func.apply(block.getLocation().get().getRelative(Direction.NORTH).createSnapshot()) ? true : isTrue;
        isTrue = func.apply(block.getLocation().get().getRelative(Direction.NORTHEAST).createSnapshot()) ? true : isTrue;
        return isTrue;
    }

    private boolean lookAroundInRange(Function<BlockSnapshot, Boolean> func, BlockSnapshot block, int range) {
        boolean isTrue = false;
        for (int x = -range; x <= range; x++) {
            for (int y = 0; y <= range; y++) {
                for (int z = -range; z <= range; z++) {
                    isTrue = func.apply(block.getLocation().get().add(x, y, z).createSnapshot()) ? true : isTrue;
                }
            }
        }
        return isTrue;
    }

    private boolean isLog(BlockState block) {
        BlockType type = block.getType();
        return (BlockTypes.LOG.equals(type) || BlockTypes.LOG2.equals(type));
    }

    public boolean isNatureLeaf(BlockState block) {
        BlockType type = block.getType();

        if (BlockTypes.LEAVES.equals(type) || BlockTypes.LEAVES2.equals(type)) {
            Optional<ImmutableDecayableData> decayable = block.get(ImmutableDecayableData.class
            );
            if (decayable.isPresent()) {
                return decayable.get().decayable().get();
            }
        }
        return false;
    }

    private boolean isAxe(ItemStack item) {
        ItemType type = item.getItem();
        if (type.equals(ItemTypes.WOODEN_AXE) || type.equals(ItemTypes.STONE_AXE)
                || type.equals(ItemTypes.IRON_AXE) || type.equals(ItemTypes.GOLDEN_AXE)
                || type.equals(ItemTypes.DIAMOND_AXE)) {

            return true;
        }
        return false;
    }

}
