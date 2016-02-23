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
import java.util.Random;
import java.util.function.Function;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

/**
 *
 * @author Kime
 */
public class LMListener implements Listener {

    private final LumberMan plugin;
    private final Random random;
    private final LinkedHashMap<Block, Block> treeCache;
    private final LinkedList<Block> branchsCache;
    private final LinkedHashMap<Block, Block> leavesCache;

    private final int BRANCH_DEEP = 5;
    private final int LEAVES_DEEP = 1;

    public LMListener(LumberMan plugin) {
        this.plugin = plugin;

        random = new Random();

        treeCache = new LinkedHashMap<>(512);
        branchsCache = new LinkedList<>();
        leavesCache = new LinkedHashMap<>(512);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (isLog(event.getBlock())) {
            ItemStack item = event.getPlayer().getItemInHand();
            if (isAxe(item) && treeTest(event.getBlock())) {            

                treeCache.remove(event.getBlock());

                popTree(event.getPlayer());

            }
        }
        treeCache.clear();
        branchsCache.clear();
        leavesCache.clear();
    }

    private void popTree(Player player) {
        boolean isBreak = false;
        double healthDamage = 0;
        
        for (Block b : treeCache.values()) {
            b.breakNaturally();
            if (isBreak) {
                healthDamage++;
            } else if (breaksTool(player.getItemInHand())) {
                player.getInventory().clear(player.getInventory().getHeldItemSlot());
                isBreak = true;
            }
        }

        if (healthDamage > 0) {
            player.damage(healthDamage);
        }

        leavesCache.values().forEach((Block b) -> {
            b.breakNaturally();
        });
    }

    private boolean treeTest(Block base) {
        treeCache.clear();
        branchsCache.clear();
        leavesCache.clear();
        Block block = base;
        while (isLog(block)) {
            treeCache.put(block, block);

            lookAroundInRange((Block b) -> {
                if (isNatureLeaf(b) && !leavesCache.containsKey(b)) {
                    leavesCache.put(b, b);
                }
                return false;
            }, block, LEAVES_DEEP);

            //find branch
            lookAround((Block t) -> {
                if (isLog(t)) {
                    branchsCache.addFirst(t);
                }
                return false;
            }, block);

            block = block.getRelative(BlockFace.UP);
        }

        lookAround((Block t) -> {
            if (isLog(t)) {
                branchsCache.addFirst(t);
            }
            return false;
        }, block);

        if ((isNatureLeaf(block)) || (!branchsCache.isEmpty() && branchTest(branchsCache.removeFirst(), 1))) {
            branchsCache.forEach(b -> branchTest(b, 1));
            return true;
        } else {
            return false;
        }

    }

    private boolean branchTest(Block block, final int count) {
        if (!isLog(block)) {
            return false;
        }
        if (count > BRANCH_DEEP) {
            return false;
        }
        if (treeCache.containsKey(block)) {
            return false;
        }

        treeCache.put(block, block);

        lookAroundInRange((Block b) -> {
            if (isNatureLeaf(b) && !leavesCache.containsKey(b)) {
                leavesCache.put(b, b);
            }
            return false;
        }, block, LEAVES_DEEP);

        boolean isTree = false;

        Block up = block.getRelative(BlockFace.UP);

        isTree = branchTest(up, count + 1) ? true : isTree;
        isTree = lookAround((Block t) -> branchTest(t, count + 1), up) ? true : isTree;
        isTree = lookAround((Block t) -> branchTest(t, count + 1), block) ? true : isTree;

        return isTree || isNatureLeaf(up);
    }

    private boolean breaksTool(ItemStack item) {
        if ((item != null)) {
            short durability = item.getDurability();
            short maxDurability = item.getType().getMaxDurability();
            if (durability < maxDurability) {
                int level = item.getEnchantmentLevel(Enchantment.DURABILITY);

                durability += (random.nextInt(100) <= (100.0 / (level + 1))) ? 1 : 0;

                item.setDurability(durability);

                if (durability >= maxDurability) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean lookAround(Function<Block, Boolean> func, Block block) {
        boolean isTrue = false;
        isTrue = func.apply(block.getRelative(BlockFace.EAST)) ? true : isTrue;
        isTrue = func.apply(block.getRelative(BlockFace.SOUTH_EAST)) ? true : isTrue;
        isTrue = func.apply(block.getRelative(BlockFace.SOUTH)) ? true : isTrue;
        isTrue = func.apply(block.getRelative(BlockFace.SOUTH_WEST)) ? true : isTrue;
        isTrue = func.apply(block.getRelative(BlockFace.WEST)) ? true : isTrue;
        isTrue = func.apply(block.getRelative(BlockFace.NORTH_WEST)) ? true : isTrue;
        isTrue = func.apply(block.getRelative(BlockFace.NORTH)) ? true : isTrue;
        isTrue = func.apply(block.getRelative(BlockFace.NORTH_EAST)) ? true : isTrue;
        return isTrue;
    }

    private boolean lookAroundInRange(Function<Block, Boolean> func, Block block, int range) {
        boolean isTrue = false;
        for (int x = -range; x <= range; x++) {
            for (int y = 0; y <= range; y++) {
                for (int z = -range; z <= range; z++) {
                    isTrue = func.apply(block.getRelative(x, y, z)) ? true : isTrue;
                }
            }
        }
        return isTrue;
    }

    private boolean isLog(Block block) {
        Material type = block.getType();
        return (Material.LOG.equals(type) || Material.LOG_2.equals(type));
    }

    public boolean isNatureLeaf(Block block) {
        Material type = block.getType();
        if (Material.LEAVES.equals(type) || Material.LEAVES_2.equals(type)) {
            byte data = block.getData();
            return ((data & 4) != 4);
        }
        return false;
    }

    private boolean isAxe(ItemStack item) {
        switch (item.getType()) {
            case WOOD_AXE:
            case STONE_AXE:
            case IRON_AXE:
            case GOLD_AXE:
            case DIAMOND_AXE:
                return true;
            default:
                return false;

        }
    }

}
