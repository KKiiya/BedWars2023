/*
 * BedWars2023 - A bed wars mini-game.
 * Copyright (C) 2024 Tomas Keuper
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Contact e-mail: contact@fyreblox.com
 */

package com.tomkeuper.bedwars.shop.main;

import com.tomkeuper.bedwars.BedWars;
import com.tomkeuper.bedwars.api.arena.IArena;
import com.tomkeuper.bedwars.api.arena.shop.IBuyItem;
import com.tomkeuper.bedwars.api.arena.shop.ICategoryContent;
import com.tomkeuper.bedwars.api.arena.shop.IContentTier;
import com.tomkeuper.bedwars.api.configuration.ConfigPath;
import com.tomkeuper.bedwars.api.events.shop.ShopBuyEvent;
import com.tomkeuper.bedwars.api.language.Language;
import com.tomkeuper.bedwars.api.language.Messages;
import com.tomkeuper.bedwars.api.shop.IPlayerQuickBuyCache;
import com.tomkeuper.bedwars.api.shop.IQuickBuyElement;
import com.tomkeuper.bedwars.api.shop.IShopCache;
import com.tomkeuper.bedwars.api.shop.IShopCategory;
import com.tomkeuper.bedwars.arena.Arena;
import com.tomkeuper.bedwars.configuration.Sounds;
import com.tomkeuper.bedwars.shop.ShopCache;
import com.tomkeuper.bedwars.shop.quickbuy.PlayerQuickBuyCache;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

import static com.tomkeuper.bedwars.api.language.Language.getMsg;

@SuppressWarnings("WeakerAccess")
public class CategoryContent implements ICategoryContent {

    private final List<IContentTier> contentTiers = new ArrayList<>();
    private final IShopCategory father;
    private int slot;
    private boolean loaded = false;
    private final String contentName;
    private String itemNamePath, itemLorePath;
    private String identifier;
    private String categoryIdentifier;
    private boolean permanent = false, downgradable = false, unbreakable = false;
    private byte weight = 0;


    /**
     * Load a new category
     */
    public CategoryContent(String path, String name, String categoryName, YamlConfiguration yml, IShopCategory father) {
        BedWars.debug("Loading CategoryContent " + path + " name: " + name);
        this.contentName = name;
        this.father = father;

        if (path == null || name == null || categoryName == null || yml == null) return;

        if (yml.get(path + "." + ConfigPath.SHOP_CATEGORY_CONTENT_CONTENT_SLOT) == null) {
            BedWars.plugin.getLogger().severe("Content slot not set at " + path);
            return;
        }

        if (yml.get(path + "." + ConfigPath.SHOP_CATEGORY_CONTENT_CONTENT_TIERS) == null) {
            BedWars.plugin.getLogger().severe("No tiers set for " + path);
            return;
        }

        if (yml.getConfigurationSection(path + "." + ConfigPath.SHOP_CATEGORY_CONTENT_CONTENT_TIERS).getKeys(false).isEmpty()) {
            BedWars.plugin.getLogger().severe("No tiers set for " + path);
            return;
        }

        if (yml.get(path + "." + ConfigPath.SHOP_CATEGORY_CONTENT_CONTENT_TIERS + ".tier1") == null) {
            BedWars.plugin.getLogger().severe("tier1 not found for " + path);
            return;
        }

        if (yml.get(path + "." + ConfigPath.SHOP_CATEGORY_CONTENT_IS_PERMANENT) != null) {
            permanent = yml.getBoolean(path + "." + ConfigPath.SHOP_CATEGORY_CONTENT_IS_PERMANENT);
        }

        if (yml.get(path + "." + ConfigPath.SHOP_CATEGORY_CONTENT_IS_DOWNGRADABLE) != null) {
            downgradable = yml.getBoolean(path + "." + ConfigPath.SHOP_CATEGORY_CONTENT_IS_DOWNGRADABLE);
        }

        if (yml.get(path + "." + ConfigPath.SHOP_CATEGORY_CONTENT_IS_UNBREAKABLE) != null) {
            unbreakable = yml.getBoolean(path + "." + ConfigPath.SHOP_CATEGORY_CONTENT_IS_UNBREAKABLE);
        }

        if (yml.get(path + "." + ConfigPath.SHOP_CATEGORY_CONTENT_WEIGHT) != null) {
            weight = (byte) yml.getInt(path + "." + ConfigPath.SHOP_CATEGORY_CONTENT_WEIGHT);
        }

        this.slot = yml.getInt(path + "." + ConfigPath.SHOP_CATEGORY_CONTENT_CONTENT_SLOT);

        ContentTier ctt;
        for (String s : yml.getConfigurationSection(path + "." + ConfigPath.SHOP_CATEGORY_CONTENT_CONTENT_TIERS).getKeys(false)) {
            ctt = new ContentTier(path + "." + ConfigPath.SHOP_CATEGORY_CONTENT_CONTENT_TIERS + "." + s, s, path, yml);
            /*if (ctt.isLoaded())*/
            contentTiers.add(ctt);
        }

        itemNamePath = Messages.SHOP_CONTENT_TIER_ITEM_NAME.replace("%category%", categoryName).replace("%content%", contentName);
        for (Language lang : Language.getLanguages()) {
            if (!lang.exists(itemNamePath)) {
                lang.set(itemNamePath, "&cName not set");
            }
        }
        itemLorePath = Messages.SHOP_CONTENT_TIER_ITEM_LORE.replace("%category%", categoryName).replace("%content%", contentName);
        for (Language lang : Language.getLanguages()) {
            if (!lang.exists(itemLorePath)) {
                lang.set(itemLorePath, "&cLore not set");
            }
        }

        identifier = path;
        categoryIdentifier = path;

        loaded = true;
    }

    @Override
    public boolean execute(Player player, IShopCache shopCache, int slot) {
        IContentTier ct;

        //check weight
        if (shopCache.getCategoryWeight(father) > weight) {
            player.sendMessage(getMsg(player, Messages.SHOP_ALREADY_HIGHER_TIER));
            Sounds.playSound(ConfigPath.SOUNDS_INSUFF_MONEY, player);
            return false;
        }

        if (shopCache.getContentTier(getIdentifier()) > contentTiers.size()) {
            Bukkit.getLogger().severe("Wrong tier order at: " + getIdentifier());
            return false;
        }

        //check if can re-buy
        if (shopCache.getContentTier(getIdentifier()) == contentTiers.size()) {
            if (isPermanent() && shopCache.hasCachedItem(this)) {
                player.sendMessage(getMsg(player, Messages.SHOP_ALREADY_BOUGHT));
                Sounds.playSound(ConfigPath.SOUNDS_INSUFF_MONEY, player);
                return false;
            }
            // Current tier
            ct = contentTiers.get(shopCache.getContentTier(getIdentifier()) - 1);
        } else {
            if (!shopCache.hasCachedItem(this)) ct = contentTiers.get(0);
            else ct = contentTiers.get(shopCache.getContentTier(getIdentifier()));
        }

        // Check money
        int money = calculateMoney(player, ct.getCurrency());
        if (money < ct.getPrice()) {
            player.sendMessage(getMsg(player, Messages.SHOP_INSUFFICIENT_MONEY).replace("%bw_currency%", getMsg(player, getCurrencyMsgPath(ct))).
                    replace("%bw_amount%", String.valueOf(ct.getPrice() - money)));
            Sounds.playSound(ConfigPath.SOUNDS_INSUFF_MONEY, player);
            return false;
        }

        ShopBuyEvent event = new ShopBuyEvent(player, Arena.getArenaByPlayer(player), this, shopCache, slot);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return false;


        // Check inventory has space
        if (player.getInventory().firstEmpty() == -1){
            Sounds.playSound(ConfigPath.SOUNDS_INSUFF_MONEY, player);
            player.sendMessage(getMsg(player, Messages.UPGRADES_LORE_REPLACEMENT_INSUFFICIENT_SPACE));
            return false;
        }

        // Take money
        takeMoney(player, ct.getCurrency(), ct.getPrice());

        // Upgrade if possible
        shopCache.upgradeCachedItem(this, slot);

        // Give items
        giveItems(player, event.getShopCache(), Arena.getArenaByPlayer(player));

        // Play sound
        Sounds.playSound(ConfigPath.SOUNDS_BOUGHT, player);

        // Send purchase msg
        if (itemNamePath == null || Language.getPlayerLanguage(player).getYml().get(itemNamePath) == null) {
            ItemStack displayItem = ct.getItemStack();
            if (displayItem.getItemMeta() != null && displayItem.getItemMeta().hasDisplayName()) {
                player.sendMessage(getMsg(player, Messages.SHOP_NEW_PURCHASE).replace("%bw_item%", displayItem.getItemMeta().getDisplayName()));
            }
        } else {
            if (isUpgradable()) {
                int tierI = ct.getValue();
                String tier = getRomanNumber(tierI);
                player.sendMessage(getMsg(player, Messages.SHOP_NEW_PURCHASE).replace("%bw_item%", ChatColor.stripColor(getMsg(player, itemNamePath))).replace("%bw_color%", "").replace("%bw_tier%", tier));
            } else {
                player.sendMessage(getMsg(player, Messages.SHOP_NEW_PURCHASE).replace("%bw_item%", ChatColor.stripColor(getMsg(player, itemNamePath))).replace("%bw_color%", "").replace("%bw_tier%", ""));
            }
        }
        shopCache.setCategoryWeight(father, weight);
        return true;
    }

    /**
     * Add tier items to player inventory
     */
    @Override
    public void giveItems(Player player, IShopCache shopCache, IArena arena) {
        for (IBuyItem bi : contentTiers.get(shopCache.getContentTier(getIdentifier()) - 1).getBuyItemsList()) {
            bi.give(player, arena);
        }
    }

    @Override
    public int getSlot() {
        return slot;
    }

    @Override
    public ItemStack getItemStack(Player player) {
        ShopCache sc = ShopCache.getInstance().getShopCache(player.getUniqueId());
        return sc == null ? null : getItemStack(player, sc);
    }

    @Override
    public boolean hasQuick(Player player) {
        IPlayerQuickBuyCache pqbc = PlayerQuickBuyCache.getInstance().getQuickBuyCache(player.getUniqueId());
        return pqbc != null && hasQuick(pqbc);
    }

    @Override
    public ItemStack getItemStack(Player player, IShopCache shopCache) {
        IContentTier ct;
        if (shopCache.getContentTier(identifier) == contentTiers.size()) ct = contentTiers.get(contentTiers.size() - 1);
        else {
            if (shopCache.hasCachedItem(this)) ct = contentTiers.get(shopCache.getContentTier(identifier));
            else ct = contentTiers.get(shopCache.getContentTier(identifier) - 1);
        }

        ItemStack i = ct.getItemStack();
        ItemMeta im = i.getItemMeta();

        if (im != null) {
            im = i.getItemMeta().clone();
            boolean canAfford = calculateMoney(player, ct.getCurrency()) >= ct.getPrice();
            IPlayerQuickBuyCache qbc = PlayerQuickBuyCache.getInstance().getQuickBuyCache(player.getUniqueId());
            boolean hasQuick = qbc != null && hasQuick(qbc);

            String color = getMsg(player, canAfford ? Messages.SHOP_CAN_BUY_COLOR : Messages.SHOP_CANT_BUY_COLOR);
            String translatedCurrency = getMsg(player, getCurrencyMsgPath(ct));
            ChatColor cColor = getCurrencyColor(ct.getCurrency());

            int tierI = ct.getValue();
            String tier = getRomanNumber(tierI);
            String buyStatus;

            if (isPermanent() && shopCache.hasCachedItem(this) && shopCache.getCachedItem(this).getTier() == getContentTiers().size()) {
                if (!(BedWars.nms.isArmor(i))) buyStatus = getMsg(player, Messages.SHOP_LORE_STATUS_MAXED);  //ARMOR
                else buyStatus = getMsg(player, Messages.SHOP_LORE_STATUS_ARMOR);
            } else if (!canAfford) buyStatus = getMsg(player, Messages.SHOP_LORE_STATUS_CANT_AFFORD).replace("%bw_currency%", translatedCurrency);
            else buyStatus = getMsg(player, Messages.SHOP_LORE_STATUS_CAN_BUY);

            im.setDisplayName(getMsg(player, itemNamePath).replace("%bw_color%", color).replace("%bw_tier%", tier));

            List<String> lore = new ArrayList<>();

            for (String s : Language.getList(player, itemLorePath)) {
                if (s.contains("%bw_quick_buy%")) {
                    if (!hasQuick) {
                        s = getMsg(player, Messages.SHOP_LORE_QUICK_ADD);
                        continue;
                    }

                    if (ShopIndex.getIndexViewers().contains(player.getUniqueId())) {
                        s = getMsg(player, Messages.SHOP_LORE_QUICK_REMOVE);
                    } else continue;
                }
                s = s.replace("%bw_tier%", tier).replace("%bw_color%", color).replace("%bw_cost%", cColor + String.valueOf(ct.getPrice()))
                        .replace("%bw_currency%", cColor + translatedCurrency).replace("%bw_buy_status%", buyStatus);
                lore.add(s);
            }

            im.setLore(lore);
            i.setItemMeta(im);
        }
        return i;
    }

    public boolean hasQuick(IPlayerQuickBuyCache c) {
        for (IQuickBuyElement q : c.getElements()) if (q.getCategoryContent() == this) return true;
        return false;
    }

    /**
     * Get player's money amount
     */
    public static int calculateMoney(Player player, Material currency) {
        if (currency == Material.AIR) return (int) BedWars.getEconomy().getMoney(player);

        int amount = 0;
        for (ItemStack is : player.getInventory().getContents()) {
            if (is == null) continue;
            if (is.getType() == currency) amount += is.getAmount();
        }
        return amount;
    }

    /**
     * Get currency as material
     */
    public static Material getCurrency(String currency) {
        Material material;
        switch (currency) {
            default:
                material = Material.IRON_INGOT;
                break;
            case "gold":
                material = Material.GOLD_INGOT;
                break;
            case "diamond":
                material = Material.DIAMOND;
                break;
            case "emerald":
                material = Material.EMERALD;
                break;
            case "vault":
                material = Material.AIR;
                break;
        }
        return material;
    }

    public static ChatColor getCurrencyColor(Material currency) {
        ChatColor c = ChatColor.DARK_GREEN;
        if (currency.toString().toLowerCase().contains("diamond")) c = ChatColor.AQUA;
        else if (currency.toString().toLowerCase().contains("gold")) c = ChatColor.GOLD;
        else if (currency.toString().toLowerCase().contains("iron")) c = ChatColor.WHITE;
        return c;
    }

    /**
     * Cet currency path
     */
    public static String getCurrencyMsgPath(IContentTier contentTier) {
        String c;

        if (contentTier.getCurrency().toString().toLowerCase().contains("iron")) {
            c = contentTier.getPrice() == 1 ? Messages.MEANING_IRON_SINGULAR : Messages.MEANING_IRON_PLURAL;
        } else if (contentTier.getCurrency().toString().toLowerCase().contains("gold")) {
            c = contentTier.getPrice() == 1 ? Messages.MEANING_GOLD_SINGULAR : Messages.MEANING_GOLD_PLURAL;
        } else if (contentTier.getCurrency().toString().toLowerCase().contains("emerald")) {
            c = contentTier.getPrice() == 1 ? Messages.MEANING_EMERALD_SINGULAR : Messages.MEANING_EMERALD_PLURAL;
        } else if (contentTier.getCurrency().toString().toLowerCase().contains("diamond")) {
            c = contentTier.getPrice() == 1 ? Messages.MEANING_DIAMOND_SINGULAR : Messages.MEANING_DIAMOND_PLURAL;
        } else {
            c = contentTier.getPrice() == 1 ? Messages.MEANING_VAULT_SINGULAR : Messages.MEANING_VAULT_PLURAL;
        }
        return c;
    }

    /**
     * Get the roman number for an integer
     */
    public static String getRomanNumber(int n) {
        String s;
        switch (n) {
            default:
                s = String.valueOf(n);
                break;
            case 1:
                s = "I";
                break;
            case 2:
                s = "II";
                break;
            case 3:
                s = "III";
                break;
            case 4:
                s = "IV";
                break;
            case 5:
                s = "V";
                break;
            case 6:
                s = "VI";
                break;
            case 7:
                s = "VII";
                break;
            case 8:
                s = "VIII";
                break;
            case 9:
                s = "IX";
                break;
            case 10:
                s = "X";
                break;
        }
        return s;
    }


    /**
     * Take money from player on buy
     */
    public static void takeMoney(Player player, Material currency, int amount) {
        if (currency == Material.AIR) {
            if (!BedWars.getEconomy().isEconomy()) {
                player.sendMessage("§4§lERROR: This requires Vault Support! Please install Vault plugin!");
                return;
            }
            BedWars.getEconomy().buyAction(player, amount);
            return;
        }

        int cost = amount;
        for (ItemStack i : player.getInventory().getContents()) {
            if (i == null) continue;
            if (i.getType() == currency) {
                if (i.getAmount() < cost) {
                    cost -= i.getAmount();
                    BedWars.nms.minusAmount(player, i, i.getAmount());
                    player.updateInventory();
                } else {
                    BedWars.nms.minusAmount(player, i, cost);
                    player.updateInventory();
                    break;
                }
            }
        }

    }

    public void setLoaded(boolean loaded) {
        this.loaded = loaded;
    }

    /**
     * Check if category content was loaded
     */
    public boolean isLoaded() {
        return loaded;
    }

    public boolean isPermanent() {
        return permanent;
    }

    public boolean isDowngradable() {
        return downgradable;
    }

    public boolean isUpgradable() {
        return getContentTiers().size() > 1;
    }

    public String getIdentifier() {
        return identifier;
    }

    public List<IContentTier> getContentTiers() {
        return contentTiers;
    }

    public String getCategoryIdentifier() {
        return categoryIdentifier;
    }

    public void setCategoryIdentifier(String categoryIdentifier) {
        this.categoryIdentifier = categoryIdentifier;
    }
}
