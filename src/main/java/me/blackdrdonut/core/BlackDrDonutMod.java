package me.blackdrdonut.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.scoreboard.*;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.text.NumberFormat;
import java.util.*;
import java.util.stream.Collectors;

public class BlackDrDonutMod implements ModInitializer {

    public static final String SERVER_TITLE = "§8§l« §5§lBlack§d§lDrdonut§f§lSMP §8§l»";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private File dataFile;
    private SaveData data = new SaveData();

    private final List<Item> catalogItems = new ArrayList<>();
    private final Map<Item, Long> itemPrices = new LinkedHashMap<>();
    private final Map<UUID, ServerBossBar> playerBossBars = new HashMap<>();
    private final Map<UUID, Integer> combatTimers = new HashMap<>();
    private final Map<UUID, UUID> tpaRequests = new HashMap<>();
    private final Set<UUID> afkPlayers = new HashSet<>();
    private int tickCounter = 0;

    public static class SaveData {
        public Map<String, PlayerStats> players = new HashMap<>();
        public List<OrderEntry> orders = new ArrayList<>();
        public List<AuctionEntry> auctions = new ArrayList<>();
        public Map<String, List<DeliveredItem>> deliveries = new HashMap<>();
        public Double lbX = null, lbY = null, lbZ = null;
    }

    public static class PlayerStats {
        public String name = "Player";
        public long money = 2500; // Starting money
        public long shards = 10;
        public int kills = 0;
        public int deaths = 0;
        public Double homeX = null, homeY = null, homeZ = null;
    }

    public static class OrderEntry {
        public String ownerUuid;
        public String ownerName;
        public String itemId;
        public int amountRemaining;
        public long priceEach;
    }

    public static class DeliveredItem {
        public String itemId;
        public int amount;
    }

    public static class AuctionEntry {
        public String sellerUuid;
        public String sellerName;
        public String itemId;
        public int count;
        public long price;
    }

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            loadData();
            initAllBlockAndItemPrices();
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> saveData());

        registerCommands();
        registerEvents();
    }

    // =========================================================================
    // 1. ALL BLOCK & ITEM PRICING (SAND = $599, OTHERS HIGHER, RARES VERY HIGH)
    // =========================================================================
    private void initAllBlockAndItemPrices() {
        itemPrices.clear();
        catalogItems.clear();

        // Explicit Base Prices: Sand is exactly $599 as requested!
        itemPrices.put(Items.SAND, 599L);
        itemPrices.put(Items.RED_SAND, 699L);

        // Common Blocks (Higher than Sand: $650 - $2,500)
        setPrice(Items.DIRT, 650L);
        setPrice(Items.COARSE_DIRT, 700L);
        setPrice(Items.GRASS_BLOCK, 850L);
        setPrice(Items.COBBLESTONE, 700L);
        setPrice(Items.STONE, 800L);
        setPrice(Items.SMOOTH_STONE, 950L);
        setPrice(Items.STONE_BRICKS, 900L);
        setPrice(Items.GRAVEL, 680L);
        setPrice(Items.NETHERRACK, 650L);
        setPrice(Items.SANDSTONE, 850L);
        setPrice(Items.GLASS, 900L);
        setPrice(Items.OAK_LOG, 1200L);
        setPrice(Items.SPRUCE_LOG, 1200L);
        setPrice(Items.BIRCH_LOG, 1200L);
        setPrice(Items.JUNGLE_LOG, 1200L);
        setPrice(Items.ACACIA_LOG, 1200L);
        setPrice(Items.DARK_OAK_LOG, 1200L);
        setPrice(Items.CHERRY_LOG, 1400L);
        setPrice(Items.MANGROVE_LOG, 1400L);
        setPrice(Items.DEEPSLATE, 1100L);
        setPrice(Items.COBBLED_DEEPSLATE, 950L);
        setPrice(Items.TUFF, 900L);
        setPrice(Items.CALCITE, 1100L);
        setPrice(Items.BLACKSTONE, 1250L);
        setPrice(Items.BASALT, 1150L);
        setPrice(Items.END_STONE, 2200L);

        // Mid-Tier & Grinding Blocks ($3,000 - $35,000)
        setPrice(Items.QUARTZ_BLOCK, 3500L);
        setPrice(Items.SEA_LANTERN, 4800L);
        setPrice(Items.GLOWSTONE, 4500L);
        setPrice(Items.SOUL_SAND, 3200L);
        setPrice(Items.SOUL_SOIL, 3200L);
        setPrice(Items.MAGMA_BLOCK, 3800L);
        setPrice(Items.ICE, 2500L);
        setPrice(Items.PACKED_ICE, 7500L);
        setPrice(Items.BLUE_ICE, 25000L);
        setPrice(Items.SLIME_BLOCK, 18000L);
        setPrice(Items.HONEY_BLOCK, 18000L);
        setPrice(Items.PISTON, 6500L);
        setPrice(Items.STICKY_PISTON, 9500L);
        setPrice(Items.REDSTONE_BLOCK, 5500L);
        setPrice(Items.LAPIS_BLOCK, 6000L);
        setPrice(Items.COPPER_BLOCK, 4500L);
        setPrice(Items.AMETHYST_BLOCK, 5000L);
        setPrice(Items.BOOKSHELF, 4200L);

        // Raiding, Crystal PvP & Rare Items (HIGH PRICES: $25,000 - $25,000,000)
        setPrice(Items.OBSIDIAN, 25000L);
        setPrice(Items.CRYING_OBSIDIAN, 45000L);
        setPrice(Items.TNT, 18000L);
        setPrice(Items.ENDER_PEARL, 12000L);
        setPrice(Items.GOLDEN_APPLE, 35000L);
        setPrice(Items.END_CRYSTAL, 85000L);
        setPrice(Items.RESPAWN_ANCHOR, 95000L);
        setPrice(Items.IRON_BLOCK, 30000L);
        setPrice(Items.GOLD_BLOCK, 65000L);
        setPrice(Items.DIAMOND, 25000L);
        setPrice(Items.EMERALD, 20000L);
        setPrice(Items.EMERALD_BLOCK, 180000L);
        setPrice(Items.DIAMOND_BLOCK, 225000L);
        setPrice(Items.SHULKER_BOX, 350000L);
        setPrice(Items.TOTEM_OF_UNDYING, 450000L);
        setPrice(Items.ANCIENT_DEBRIS, 500000L);
        setPrice(Items.NETHERITE_INGOT, 1500000L);
        setPrice(Items.NETHERITE_BLOCK, 13500000L);
        setPrice(Items.ENCHANTED_GOLDEN_APPLE, 2500000L);
        setPrice(Items.NETHER_STAR, 3000000L);
        setPrice(Items.BEACON, 3500000L);
        setPrice(Items.ELYTRA, 5000000L);
        setPrice(Items.HEAVY_CORE, 7500000L);
        setPrice(Items.MACE, 10000000L);
        setPrice(Items.SPAWNER, 15000000L);
        setPrice(Items.DRAGON_EGG, 25000000L);

        // Automatically register EVERY remaining Block & Item in Minecraft 1.21.1
        for (Item item : Registries.ITEM) {
            if (item == Items.AIR) continue;
            if (!itemPrices.containsKey(item)) {
                String id = Registries.ITEM.getId(item).getPath();
                Rarity rarity = item.getDefaultStack().getRarity();
                long autoPrice;
                if (rarity == Rarity.EPIC || id.contains("netherite") || id.contains("Silence") || id.contains("dragon")) {
                    autoPrice = 2000000L;
                } else if (rarity == Rarity.RARE || id.contains("diamond") || id.contains("emerald") || id.contains("shulker") || id.contains("beacon")) {
                    autoPrice = 180000L;
                } else if (rarity == Rarity.UNCOMMON || id.contains("gold") || id.contains("amethyst") || id.contains("quartz")) {
                    autoPrice = 25000L;
                } else if (item instanceof BlockItem) {
                    // Every block is higher than Sand ($599)
                    autoPrice = 950L;
                } else {
                    autoPrice = 1250L;
                }
                itemPrices.put(item, autoPrice);
            }
        }

        // Put Blocks first (starting with Sand & popular blocks), then all other items
        for (Map.Entry<Item, Long> entry : itemPrices.entrySet()) {
            if (entry.getKey() instanceof BlockItem) {
                catalogItems.add(entry.getKey());
            }
        }
        for (Map.Entry<Item, Long> entry : itemPrices.entrySet()) {
            if (!(entry.getKey() instanceof BlockItem)) {
                catalogItems.add(entry.getKey());
            }
        }
    }

    private void setPrice(Item item, long price) {
        itemPrices.put(item, price);
    }

    private long getPrice(Item item) {
        return itemPrices.getOrDefault(item, 950L);
    }

    // =========================================================================
    // 2. COMMANDS (/order, /shop, /sell, /ah, /leaderboard, /rtp, etc.)
    // =========================================================================
    private void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {

            // /order and /orders
            dispatcher.register(CommandManager.literal("order")
                .executes(ctx -> {
                    openOrdersMainGui(ctx.getSource().getPlayerOrThrow(), 0);
                    return 1;
                })
                .then(CommandManager.literal("hand")
                    .then(CommandManager.argument("amount", IntegerArgumentType.integer(1, 2304))
                        .then(CommandManager.argument("priceEach", LongArgumentType.longArg(1))
                            .executes(ctx -> {
                                ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                                ItemStack hand = player.getMainHandStack();
                                if (hand.isEmpty()) {
                                    player.sendMessage(Text.literal("§cʜᴏʟᴅ ᴀɴ ɪᴛᴇᴍ ɪɴ ʏᴏᴜʀ ʜᴀɴᴅ ᴏʀ ᴜsᴇ §e/order§c!"), false);
                                    return 1;
                                }
                                int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                long priceEach = LongArgumentType.getLong(ctx, "priceEach");
                                createBuyOrder(player, hand.getItem(), amount, priceEach);
                                return 1;
                            })
                        )
                    )
                )
            );

            dispatcher.register(CommandManager.literal("orders")
                .executes(ctx -> {
                    openOrdersMainGui(ctx.getSource().getPlayerOrThrow(), 0);
                    return 1;
                })
            );

            // /shop
            dispatcher.register(CommandManager.literal("shop")
                .executes(ctx -> {
                    openShopGui(ctx.getSource().getPlayerOrThrow(), 0);
                    return 1;
                })
            );

            // /sell
            dispatcher.register(CommandManager.literal("sell")
                .executes(ctx -> {
                    openSellDropGui(ctx.getSource().getPlayerOrThrow());
                    return 1;
                })
            );

            // /ah and /ah sell <price>
            dispatcher.register(CommandManager.literal("ah")
                .executes(ctx -> {
                    openAuctionGui(ctx.getSource().getPlayerOrThrow(), 0);
                    return 1;
                })
                .then(CommandManager.literal("sell")
                    .then(CommandManager.argument("price", LongArgumentType.longArg(1))
                        .executes(ctx -> {
                            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                            ItemStack hand = player.getMainHandStack();
                            if (hand.isEmpty()) {
                                player.sendMessage(Text.literal("§cʏᴏᴜ ᴍᴜsᴛ ʜᴏʟᴅ ᴀɴ ɪᴛᴇᴍ ᴛᴏ ʟɪsᴛ ᴏɴ /ᴀʜ!"), false);
                                return 1;
                            }
                            long price = LongArgumentType.getLong(ctx, "price");
                            AuctionEntry ae = new AuctionEntry();
                            ae.sellerUuid = player.getUuidAsString();
                            ae.sellerName = player.getName().getString();
                            ae.itemId = Registries.ITEM.getId(hand.getItem()).toString();
                            ae.count = hand.getCount();
                            ae.price = price;
                            data.auctions.add(ae);
                            hand.decrement(hand.getCount());
                            saveData();
                            player.sendMessage(Text.literal("§a§l✔ §aʟɪsᴛᴇᴅ ᴏɴ ᴀᴜᴄᴛɪᴏɴ ʜᴏᴜsᴇ ғᴏʀ §2$" + formatNum(price)), false);
                            return 1;
                        })
                    )
                )
            );

            // /leaderboard, /baltop, /setleaderboard
            dispatcher.register(CommandManager.literal("leaderboard")
                .executes(ctx -> {
                    openLeaderboardGui(ctx.getSource().getPlayerOrThrow());
                    return 1;
                })
            );
            dispatcher.register(CommandManager.literal("baltop")
                .executes(ctx -> {
                    openLeaderboardGui(ctx.getSource().getPlayerOrThrow());
                    return 1;
                })
            );
            dispatcher.register(CommandManager.literal("setleaderboard")
                .requires(src -> src.hasPermissionLevel(2))
                .executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                    data.lbX = player.getX();
                    data.lbY = player.getY();
                    data.lbZ = player.getZ();
                    saveData();
                    updateHologramLeaderboard(player.getServer());
                    player.sendMessage(Text.literal("§a§l✔ §dBlackDrdonutSMP §a3ᴅ ʟᴇᴀᴅᴇʀʙᴏᴀʀᴅ sᴘᴀᴡɴᴇᴅ ʜᴇʀᴇ!"), false);
                    return 1;
                })
            );

            // /rtp
            dispatcher.register(CommandManager.literal("rtp")
                .executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                    if (isInCombat(player)) return 1;
                    openRtpGui(player);
                    return 1;
                })
            );

            // /bal & /pay
            dispatcher.register(CommandManager.literal("bal")
                .executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                    PlayerStats st = getStats(player);
                    player.sendMessage(Text.literal(SERVER_TITLE + " §7» §fʙᴀʟᴀɴᴄᴇ: §a$" + formatNum(st.money) + " §8| §d✦ " + st.shards + " sʜᴀʀᴅs"), false);
                    return 1;
                })
            );

            dispatcher.register(CommandManager.literal("pay")
                .then(CommandManager.argument("target", StringArgumentType.word())
                    .then(CommandManager.argument("amount", LongArgumentType.longArg(1))
                        .executes(ctx -> {
                            ServerPlayerEntity sender = ctx.getSource().getPlayerOrThrow();
                            String targetName = StringArgumentType.getString(ctx, "target");
                            long amount = LongArgumentType.getLong(ctx, "amount");
                            ServerPlayerEntity target = sender.getServer().getPlayerManager().getPlayer(targetName);
                            if (target == null) {
                                sender.sendMessage(Text.literal("§cᴘʟᴀʏᴇʀ ɴᴏᴛ ғᴏᴜɴᴅ!"), false);
                                return 1;
                            }
                            PlayerStats sStats = getStats(sender);
                            if (sStats.money < amount) {
                                sender.sendMessage(Text.literal("§cɴᴏᴛ ᴇɴᴏᴜɢʜ ᴍᴏɴᴇʏ!"), false);
                                return 1;
                            }
                            sStats.money -= amount;
                            getStats(target).money += amount;
                            saveData();
                            sender.sendMessage(Text.literal("§a§l✔ §aᴘᴀɪᴅ §2$" + formatNum(amount) + " §aᴛᴏ §f" + target.getName().getString()), false);
                            target.sendMessage(Text.literal("§a§l✔ §aʀᴇᴄᴇɪᴠᴇᴅ §2$" + formatNum(amount) + " §aғʀᴏᴍ §f" + sender.getName().getString()), false);
                            return 1;
                        })
                    )
                )
            );

            // /sethome, /home, /ec, /afk, /tpa, /tpaccept
            dispatcher.register(CommandManager.literal("sethome").executes(ctx -> {
                ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                PlayerStats st = getStats(p);
                st.homeX = p.getX(); st.homeY = p.getY(); st.homeZ = p.getZ();
                saveData();
                p.sendMessage(Text.literal("§a§l✔ §aʜᴏᴍᴇ sᴇᴛ!"), false);
                return 1;
            }));

            dispatcher.register(CommandManager.literal("home").executes(ctx -> {
                ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                if (isInCombat(p)) return 1;
                PlayerStats st = getStats(p);
                if (st.homeX == null) {
                    p.sendMessage(Text.literal("§cʏᴏᴜ ᴅᴏ ɴᴏᴛ ʜᴀᴠᴇ ᴀ ʜᴏᴍᴇ sᴇᴛ! ᴜsᴇ /sethome"), false);
                    return 1;
                }
                p.teleport(p.getServerWorld(), st.homeX, st.homeY, st.homeZ, p.getYaw(), p.getPitch());
                p.sendMessage(Text.literal("§a§l✔ §aᴛᴇʟᴇᴘᴏʀᴛᴇᴅ ʜᴏᴍᴇ!"), false);
                return 1;
            }));

            dispatcher.register(CommandManager.literal("ec").executes(ctx -> {
                ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                p.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                    (syncId, inv, player) -> GenericContainerScreenHandler.createGeneric9x3(syncId, inv, p.getEnderChestInventory()),
                    Text.literal("§5§lBlackDrdonutSMP §8» §dᴇɴᴅᴇʀ ᴄʜᴇsᴛ")
                ));
                return 1;
            }));

            dispatcher.register(CommandManager.literal("afk").executes(ctx -> {
                ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                if (afkPlayers.contains(p.getUuid())) {
                    afkPlayers.remove(p.getUuid());
                    p.sendMessage(Text.literal("§eʏᴏᴜ ᴀʀᴇ ɴᴏ ʟᴏɴɢᴇʀ ɪɴ ᴀғᴋ sʜᴀʀᴅ ᴍᴏᴅᴇ."), false);
                } else {
                    afkPlayers.add(p.getUuid());
                    p.sendMessage(Text.literal("§d§l✦ §dᴀғᴋ ᴍᴏᴅᴇ ᴇɴᴀʙʟᴇᴅ! ʏᴏᴜ ᴡɪʟʟ ᴇᴀʀɴ §5+1 sʜᴀʀᴅ §dᴇᴠᴇʀʏ ᴍɪɴᴜᴛᴇ."), false);
                }
                return 1;
            }));

            dispatcher.register(CommandManager.literal("tpa")
                .then(CommandManager.argument("target", StringArgumentType.word())
                    .executes(ctx -> {
                        ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                        ServerPlayerEntity target = p.getServer().getPlayerManager().getPlayer(StringArgumentType.getString(ctx, "target"));
                        if (target == null) {
                            p.sendMessage(Text.literal("§cᴘʟᴀʏᴇʀ ɴᴏᴛ ғᴏᴜɴᴅ!"), false);
                            return 1;
                        }
                        tpaRequests.put(target.getUuid(), p.getUuid());
                        p.sendMessage(Text.literal("§aᴛᴘᴀ ʀᴇǫᴜᴇsᴛ sᴇɴᴛ ᴛᴏ §f" + target.getName().getString()), false);
                        target.sendMessage(Text.literal("§e" + p.getName().getString() + " §7ᴡᴀɴᴛs ᴛᴏ ᴛᴇʟᴇᴘᴏʀᴛ ᴛᴏ ʏᴏᴜ! ᴛʏᴘᴇ §a/tpaccept"), false);
                        return 1;
                    })
                )
            );

            dispatcher.register(CommandManager.literal("tpaccept").executes(ctx -> {
                ServerPlayerEntity target = ctx.getSource().getPlayerOrThrow();
                UUID reqUuid = tpaRequests.remove(target.getUuid());
                if (reqUuid == null) {
                    target.sendMessage(Text.literal("§cɴᴏ ᴘᴇɴᴅɪɴɢ ᴛᴘᴀ ʀᴇǫᴜᴇsᴛs!"), false);
                    return 1;
                }
                ServerPlayerEntity requester = target.getServer().getPlayerManager().getPlayer(reqUuid);
                if (requester != null && !isInCombat(requester)) {
                    requester.teleport(target.getServerWorld(), target.getX(), target.getY(), target.getZ(), target.getYaw(), target.getPitch());
                    requester.sendMessage(Text.literal("§a§l✔ §aᴛᴇʟᴇᴘᴏʀᴛᴇᴅ ᴛᴏ " + target.getName().getString()), false);
                }
                return 1;
            }));
        });
    }

    // =========================================================================
    // 3. /ORDER SYSTEM GUI (ACTIVE ORDERS + ALL BLOCK CATALOG + DELIVERIES)
    // =========================================================================
    private void openOrdersMainGui(ServerPlayerEntity player, int page) {
        SimpleInventory inv = new SimpleInventory(54);
        int start = page * 45;

        for (int i = 0; i < 45; i++) {
            int idx = start + i;
            if (idx >= data.orders.size()) break;
            OrderEntry oe = data.orders.get(idx);
            Item item = Registries.ITEM.get(Identifier.of(oe.itemId));
            ItemStack icon = new ItemStack(item);
            setItemMeta(icon, "§e§lᴏʀᴅᴇʀ: §f" + icon.getName().getString(), List.of(
                "§7ʙᴜʏᴇʀ: §f" + oe.ownerName,
                "§7ɴᴇᴇᴅᴇᴅ: §a" + oe.amountRemaining + "x",
                "§7ᴘʀɪᴄᴇ ᴇᴀᴄʜ: §2$" + formatNum(oe.priceEach),
                "§7ᴛᴏᴛᴀʟ ᴠᴀʟᴜᴇ: §6$" + formatNum(oe.priceEach * oe.amountRemaining),
                "",
                "§a§l» ᴄʟɪᴄᴋ ᴛᴏ ᴅᴇʟɪᴠᴇʀ ɪᴛᴇᴍs & ɢᴇᴛ ᴘᴀɪᴅ!"
            ));
            inv.setStack(i, icon);
        }

        // Bottom Navigation Bar
        inv.setStack(45, createButton(Items.ARROW, "§e« ᴘʀᴇᴠɪᴏᴜs ᴘᴀɢᴇ", List.of("§7ɢᴏ ᴛᴏ ᴘᴀɢᴇ " + page)));
        inv.setStack(48, createButton(Items.EMERALD, "§a§l+ ᴄʀᴇᴀᴛᴇ ᴏʀᴅᴇʀ (ᴀʟʟ ʙʟᴏᴄᴋs)", List.of(
            "§7ʙʀᴏᴡsᴇ ᴀʟʟ ʙʟᴏᴄᴋs & ɪᴛᴇᴍs ᴡɪᴛʜ ᴘʀɪᴄᴇs!",
            "§7sᴀɴᴅ = §a$599 §8| §7ʀᴀʀᴇ ʙʟᴏᴄᴋs = §6ʜɪɢʜ ᴘʀɪᴄᴇ",
            "",
            "§e» ᴄʟɪᴄᴋ ᴛᴏ ᴏᴘᴇɴ ʙʟᴏᴄᴋ ᴄᴀᴛᴀʟᴏɢ"
        )));
        inv.setStack(50, createButton(Items.CHEST_MINECART, "§b§lᴄʟᴀɪᴍ ᴅᴇʟɪᴠᴇʀᴇᴅ ɪᴛᴇᴍs", List.of(
            "§7ᴄʟɪᴄᴋ ᴛᴏ ᴄᴏʟʟᴇᴄᴛ ɪᴛᴇᴍs ᴘʟᴀʏᴇʀs",
            "§7sᴏʟᴅ ᴛᴏ ʏᴏᴜʀ ʙᴜʏ ᴏʀᴅᴇʀs!"
        )));
        inv.setStack(53, createButton(Items.ARROW, "§eɴᴇxᴛ ᴘᴀɢᴇ »", List.of("§7ɢᴏ ᴛᴏ ᴘᴀɢᴇ " + (page + 2))));

        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, playerInv, p) ->
            new GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X6, syncId, playerInv, inv, 6) {
                @Override
                public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity clicker) {
                    if (slotIndex < 0 || slotIndex >= 54) return;
                    if (slotIndex == 45 && page > 0) {
                        openOrdersMainGui(player, page - 1);
                    } else if (slotIndex == 53 && (page + 1) * 45 < data.orders.size()) {
                        openOrdersMainGui(player, page + 1);
                    } else if (slotIndex == 48) {
                        openOrderBlockCatalogGui(player, 0);
                    } else if (slotIndex == 50) {
                        claimOrderDeliveries(player);
                    } else if (slotIndex < 45) {
                        int orderIdx = start + slotIndex;
                        if (orderIdx < data.orders.size()) {
                            fulfillOrder(player, orderIdx);
                            openOrdersMainGui(player, page);
                        }
                    }
                }
            }, Text.literal("§5§lBlackDrdonutSMP §8» §e/ᴏʀᴅᴇʀs (ᴘᴀɢᴇ " + (page + 1) + ")")));
    }

    // Browse ALL Blocks & Items to create an order directly from GUI
    private void openOrderBlockCatalogGui(ServerPlayerEntity player, int page) {
        SimpleInventory inv = new SimpleInventory(54);
        int start = page * 45;

        for (int i = 0; i < 45; i++) {
            int idx = start + i;
            if (idx >= catalogItems.size()) break;
            Item item = catalogItems.get(idx);
            long marketPrice = getPrice(item);
            ItemStack stack = new ItemStack(item);
            setItemMeta(stack, "§6§l" + stack.getName().getString(), List.of(
                "§7ᴍᴀʀᴋᴇᴛ ᴘʀɪᴄᴇ: §a$" + formatNum(marketPrice) + " ᴇᴀᴄʜ",
                "§716x ᴏʀᴅᴇʀ ᴄᴏsᴛ: §2$" + formatNum(marketPrice * 16),
                "§764x ᴏʀᴅᴇʀ ᴄᴏsᴛ: §2$" + formatNum(marketPrice * 64),
                "",
                "§e» ʟᴇғᴛ-ᴄʟɪᴄᴋ: §fᴏʀᴅᴇʀ 16x @ $" + formatNum(marketPrice),
                "§b» ʀɪɢʜᴛ-ᴄʟɪᴄᴋ: §fᴏʀᴅᴇʀ 64x @ $" + formatNum(marketPrice)
            ));
            inv.setStack(i, stack);
        }

        inv.setStack(45, createButton(Items.ARROW, "§e« ᴘʀᴇᴠɪᴏᴜs ᴘᴀɢᴇ", List.of()));
        inv.setStack(49, createButton(Items.BARRIER, "§c« ʙᴀᴄᴋ ᴛᴏ ᴀᴄᴛɪᴠᴇ ᴏʀᴅᴇʀs", List.of()));
        inv.setStack(53, createButton(Items.ARROW, "§eɴᴇxᴛ ᴘᴀɢᴇ »", List.of()));

        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, playerInv, p) ->
            new GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X6, syncId, playerInv, inv, 6) {
                @Override
                public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity clicker) {
                    if (slotIndex < 0 || slotIndex >= 54) return;
                    if (slotIndex == 45 && page > 0) {
                        openOrderBlockCatalogGui(player, page - 1);
                    } else if (slotIndex == 53 && (page + 1) * 45 < catalogItems.size()) {
                        openOrderBlockCatalogGui(player, page + 1);
                    } else if (slotIndex == 49) {
                        openOrdersMainGui(player, 0);
                    } else if (slotIndex < 45) {
                        int idx = start + slotIndex;
                        if (idx < catalogItems.size()) {
                            Item chosen = catalogItems.get(idx);
                            int amount = (button == 1) ? 64 : 16;
                            createBuyOrder(player, chosen, amount, getPrice(chosen));
                            openOrdersMainGui(player, 0);
                        }
                    }
                }
            }, Text.literal("§5§lBlackDrdonutSMP §8» §aᴏʀᴅᴇʀ ʙʟᴏᴄᴋs (" + (page + 1) + ")")));
    }

    private void createBuyOrder(ServerPlayerEntity player, Item item, int amount, long priceEach) {
        long totalCost = priceEach * amount;
        PlayerStats stats = getStats(player);
        if (stats.money < totalCost) {
            player.sendMessage(Text.literal("§c§l✖ §cʏᴏᴜ ɴᴇᴇᴅ §a$" + formatNum(totalCost) + " §cᴛᴏ ᴘʟᴀᴄᴇ ᴛʜɪs ᴏʀᴅᴇʀ!"), false);
            playSound(player, false);
            return;
        }
        stats.money -= totalCost;
        OrderEntry oe = new OrderEntry();
        oe.ownerUuid = player.getUuidAsString();
        oe.ownerName = player.getName().getString();
        oe.itemId = Registries.ITEM.getId(item).toString();
        oe.amountRemaining = amount;
        oe.priceEach = priceEach;
        data.orders.add(oe);
        saveData();

        player.sendMessage(Text.literal("§a§l✔ §aᴄʀᴇᴀᴛᴇᴅ ʙᴜʏ ᴏʀᴅᴇʀ ғᴏʀ §e" + amount + "x " + item.getName().getString() + " §a@ §2$" + formatNum(priceEach) + " ᴇᴀᴄʜ!"), false);
        playSound(player, true);
    }

    private void fulfillOrder(ServerPlayerEntity seller, int orderIdx) {
        OrderEntry oe = data.orders.get(orderIdx);
        Item targetItem = Registries.ITEM.get(Identifier.of(oe.itemId));
        int available = countItemInInventory(seller, targetItem);

        if (available <= 0) {
            seller.sendMessage(Text.literal("§c§l✖ §cʏᴏᴜ ᴅᴏɴ'ᴛ ʜᴀᴠᴇ ᴀɴʏ §f" + targetItem.getName().getString() + " §cɪɴ ʏᴏᴜʀ ɪɴᴠᴇɴᴛᴏʀʏ!"), false);
            playSound(seller, false);
            return;
        }

        int toDeliver = Math.min(available, oe.amountRemaining);
        removeItemsFromInventory(seller, targetItem, toDeliver);
        long payout = toDeliver * oe.priceEach;
        getStats(seller).money += payout;
        oe.amountRemaining -= toDeliver;

        // Store delivered items for buyer
        List<DeliveredItem> list = data.deliveries.computeIfAbsent(oe.ownerUuid, k -> new ArrayList<>());
        DeliveredItem di = new DeliveredItem();
        di.itemId = oe.itemId;
        di.amount = toDeliver;
        list.add(di);

        if (oe.amountRemaining <= 0) {
            data.orders.remove(orderIdx);
        }
        saveData();

        seller.sendMessage(Text.literal("§a§l✔ §aᴅᴇʟɪᴠᴇʀᴇᴅ §e" + toDeliver + "x " + targetItem.getName().getString() + " §aᴀɴᴅ ᴇᴀʀɴᴇᴅ §2$" + formatNum(payout) + "§a!"), false);
        playSound(seller, true);
    }

    private void claimOrderDeliveries(ServerPlayerEntity player) {
        List<DeliveredItem> list = data.deliveries.remove(player.getUuidAsString());
        if (list == null || list.isEmpty()) {
            player.sendMessage(Text.literal("§cʏᴏᴜ ʜᴀᴠᴇ ɴᴏ ᴅᴇʟɪᴠᴇʀᴇᴅ ᴏʀᴅᴇʀ ɪᴛᴇᴍs ᴛᴏ ᴄʟᴀɪᴍ!"), false);
            playSound(player, false);
            return;
        }
        int totalClaimed = 0;
        for (DeliveredItem di : list) {
            Item item = Registries.ITEM.get(Identifier.of(di.itemId));
            int remaining = di.amount;
            while (remaining > 0) {
                int stackSize = Math.min(remaining, item.getMaxCount());
                player.getInventory().offerOrDrop(new ItemStack(item, stackSize));
                remaining -= stackSize;
                totalClaimed += stackSize;
            }
        }
        saveData();
        player.sendMessage(Text.literal("§a§l✔ §aᴄʟᴀɪᴍᴇᴅ §e" + totalClaimed + "x §aɪᴛᴇᴍs ғʀᴏᴍ ʏᴏᴜʀ ᴏʀᴅᴇʀs!"), false);
        playSound(player, true);
    }

    // =========================================================================
    // 4. /SHOP (ALL BLOCKS & ITEMS) & /SELL DROP-IN CHEST GUI
    // =========================================================================
    private void openShopGui(ServerPlayerEntity player, int page) {
        SimpleInventory inv = new SimpleInventory(54);
        int start = page * 45;

        for (int i = 0; i < 45; i++) {
            int idx = start + i;
            if (idx >= catalogItems.size()) break;
            Item item = catalogItems.get(idx);
            long buyPrice = getPrice(item);
            long sellPrice = Math.max(100L, (long) (buyPrice * 0.75));
            ItemStack icon = new ItemStack(item);
            setItemMeta(icon, "§d§l" + icon.getName().getString(), List.of(
                "§7ʙᴜʏ ᴘʀɪᴄᴇ: §a$" + formatNum(buyPrice),
                "§7sᴇʟʟ ᴘʀɪᴄᴇ: §6$" + formatNum(sellPrice),
                "",
                "§a» ʟᴇғᴛ-ᴄʟɪᴄᴋ: §fʙᴜʏ 1x",
                "§2» sʜɪғᴛ + ʟᴇғᴛ-ᴄʟɪᴄᴋ: §fʙᴜʏ 64x",
                "§e» ʀɪɢʜᴛ-ᴄʟɪᴄᴋ: §fsᴇʟʟ 1x"
            ));
            inv.setStack(i, icon);
        }

        inv.setStack(45, createButton(Items.ARROW, "§e« ᴘʀᴇᴠɪᴏᴜs ᴘᴀɢᴇ", List.of()));
        inv.setStack(49, createButton(Items.GOLD_INGOT, "§6§lʏᴏᴜʀ ʙᴀʟᴀɴᴄᴇ: §a$" + formatNum(getStats(player).money), List.of("§7ᴛʏᴘᴇ §e/sell §7ᴛᴏ ɪɴsᴛᴀɴᴛ-sᴇʟʟ ɪᴛᴇᴍs!")));
        inv.setStack(53, createButton(Items.ARROW, "§eɴᴇxᴛ ᴘᴀɢᴇ »", List.of()));

        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, playerInv, p) ->
            new GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X6, syncId, playerInv, inv, 6) {
                @Override
                public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity clicker) {
                    if (slotIndex < 0 || slotIndex >= 54) return;
                    if (slotIndex == 45 && page > 0) {
                        openShopGui(player, page - 1);
                    } else if (slotIndex == 53 && (page + 1) * 45 < catalogItems.size()) {
                        openShopGui(player, page + 1);
                    } else if (slotIndex < 45) {
                        int idx = start + slotIndex;
                        if (idx >= catalogItems.size()) return;
                        Item item = catalogItems.get(idx);
                        long buyPrice = getPrice(item);
                        long sellPrice = Math.max(100L, (long) (buyPrice * 0.75));
                        PlayerStats st = getStats(player);

                        if (button == 1) { // Right-click sell 1
                            if (countItemInInventory(player, item) > 0) {
                                removeItemsFromInventory(player, item, 1);
                                st.money += sellPrice;
                                saveData();
                                player.sendMessage(Text.literal("§a§l✔ §asᴏʟᴅ 1x " + item.getName().getString() + " ғᴏʀ §2$" + formatNum(sellPrice)), true);
                                playSound(player, true);
                            } else {
                                player.sendMessage(Text.literal("§cʏᴏᴜ ᴅᴏɴ'ᴛ ʜᴀᴠᴇ ᴛʜɪs ɪᴛᴇᴍ!"), true);
                                playSound(player, false);
                            }
                        } else { // Left-click buy 1 or 64
                            int amount = (actionType == SlotActionType.QUICK_MOVE) ? 64 : 1;
                            long cost = buyPrice * amount;
                            if (st.money >= cost) {
                                st.money -= cost;
                                player.getInventory().offerOrDrop(new ItemStack(item, amount));
                                saveData();
                                player.sendMessage(Text.literal("§a§l✔ §aʙᴏᴜɢʜᴛ " + amount + "x " + item.getName().getString() + " ғᴏʀ §2$" + formatNum(cost)), true);
                                playSound(player, true);
                            } else {
                                player.sendMessage(Text.literal("§cɴᴏᴛ ᴇɴᴏᴜɢʜ ᴍᴏɴᴇʏ! ɴᴇᴇᴅ §a$" + formatNum(cost)), true);
                                playSound(player, false);
                            }
                        }
                        openShopGui(player, page);
                    }
                }
            }, Text.literal("§5§lBlackDrdonutSMP §8» §d/sʜᴏᴘ (" + (page + 1) + ")")));
    }

    private void openSellDropGui(ServerPlayerEntity player) {
        SimpleInventory sellInv = new SimpleInventory(36);
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, playerInv, p) ->
            new GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X4, syncId, playerInv, sellInv, 4) {
                @Override
                public void onClosed(PlayerEntity playerEntity) {
                    super.onClosed(playerEntity);
                    long totalEarned = 0;
                    int totalSold = 0;
                    for (int i = 0; i < sellInv.size(); i++) {
                        ItemStack stack = sellInv.getStack(i);
                        if (!stack.isEmpty()) {
                            long unitSell = Math.max(100L, (long) (getPrice(stack.getItem()) * 0.80));
                            totalEarned += unitSell * stack.getCount();
                            totalSold += stack.getCount();
                            sellInv.setStack(i, ItemStack.EMPTY);
                        }
                    }
                    if (totalSold > 0) {
                        getStats(player).money += totalEarned;
                        saveData();
                        player.sendMessage(Text.literal("§a§l✔ §asᴏʟᴅ §e" + totalSold + "x ɪᴛᴇᴍs §aғᴏʀ §2$" + formatNum(totalEarned) + "§a!"), false);
                        playSound(player, true);
                    }
                }
            }, Text.literal("§5§lBlackDrdonutSMP §8» §aᴘᴜᴛ ɪᴛᴇᴍs & ᴄʟᴏsᴇ ᴛᴏ sᴇʟʟ")));
    }

    // =========================================================================
    // 5. /AH (AUCTION HOUSE GUI)
    // =========================================================================
    private void openAuctionGui(ServerPlayerEntity player, int page) {
        SimpleInventory inv = new SimpleInventory(54);
        int start = page * 45;
        for (int i = 0; i < 45; i++) {
            int idx = start + i;
            if (idx >= data.auctions.size()) break;
            AuctionEntry ae = data.auctions.get(idx);
            Item item = Registries.ITEM.get(Identifier.of(ae.itemId));
            ItemStack stack = new ItemStack(item, ae.count);
            setItemMeta(stack, "§b§l" + stack.getName().getString(), List.of(
                "§7sᴇʟʟᴇʀ: §f" + ae.sellerName,
                "§7ᴘʀɪᴄᴇ: §a$" + formatNum(ae.price),
                "",
                "§e» ᴄʟɪᴄᴋ ᴛᴏ ʙᴜʏ ɴᴏᴡ!"
            ));
            inv.setStack(i, stack);
        }
        inv.setStack(49, createButton(Items.GOLD_BLOCK, "§e§lʜᴏᴡ ᴛᴏ sᴇʟʟ", List.of("§7ʜᴏʟᴅ ᴀɴ ɪᴛᴇᴍ ᴀɴᴅ ᴛʏᴘᴇ:", "§a/ah sell <price>")));

        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, playerInv, p) ->
            new GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X6, syncId, playerInv, inv, 6) {
                @Override
                public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity clicker) {
                    if (slotIndex < 0 || slotIndex >= 45) return;
                    int idx = start + slotIndex;
                    if (idx < data.auctions.size()) {
                        AuctionEntry ae = data.auctions.get(idx);
                        PlayerStats buyerStats = getStats(player);
                        if (buyerStats.money < ae.price) {
                            player.sendMessage(Text.literal("§cɴᴏᴛ ᴇɴᴏᴜɢʜ ᴍᴏɴᴇʏ!"), false);
                            playSound(player, false);
                            return;
                        }
                        buyerStats.money -= ae.price;
                        PlayerStats sellerStats = data.players.get(ae.sellerUuid);
                        if (sellerStats != null) sellerStats.money += ae.price;
                        Item item = Registries.ITEM.get(Identifier.of(ae.itemId));
                        player.getInventory().offerOrDrop(new ItemStack(item, ae.count));
                        data.auctions.remove(idx);
                        saveData();
                        player.sendMessage(Text.literal("§a§l✔ §aᴘᴜʀᴄʜᴀsᴇᴅ ғʀᴏᴍ ᴀᴜᴄᴛɪᴏɴ ʜᴏᴜsᴇ ғᴏʀ §2$" + formatNum(ae.price)), false);
                        playSound(player, true);
                        openAuctionGui(player, page);
                    }
                }
            }, Text.literal("§5§lBlackDrdonutSMP §8» §bᴀᴜᴄᴛɪᴏɴ ʜᴏᴜsᴇ")));
    }

    // =========================================================================
    // 6. BLACKDRDONUTSMP LEADERBOARDS (GUI + SIDEBAR + 3D FLOATING HOLOGRAM)
    // =========================================================================
    private void openLeaderboardGui(ServerPlayerEntity player) {
        SimpleInventory inv = new SimpleInventory(27);
        List<PlayerStats> byMoney = new ArrayList<>(data.players.values());
        byMoney.sort((a, b) -> Long.compare(b.money, a.money));

        List<PlayerStats> byKills = new ArrayList<>(data.players.values());
        byKills.sort((a, b) -> Integer.compare(b.kills, a.kills));

        List<String> moneyLore = new ArrayList<>();
        for (int i = 0; i < Math.min(10, byMoney.size()); i++) {
            PlayerStats ps = byMoney.get(i);
            moneyLore.add("§6#" + (i + 1) + " §f" + ps.name + " §8» §a$" + formatNum(ps.money));
        }

        List<String> killLore = new ArrayList<>();
        for (int i = 0; i < Math.min(10, byKills.size()); i++) {
            PlayerStats ps = byKills.get(i);
            killLore.add("§c#" + (i + 1) + " §f" + ps.name + " §8» §e" + ps.kills + " ᴋɪʟʟs");
        }

        inv.setStack(11, createButton(Items.GOLD_BLOCK, "§6§lBlackDrdonutSMP §e§lᴛᴏᴘ ᴍᴏɴᴇʏ", moneyLore));
        inv.setStack(13, createButton(Items.BEACON, SERVER_TITLE, List.of(
            "§7ᴏғғɪᴄɪᴀʟ sᴇʀᴠᴇʀ ʟᴇᴀᴅᴇʀʙᴏᴀʀᴅs",
            "§7ᴜᴘᴅᴀᴛᴇᴅ ɪɴ ʀᴇᴀʟ-ᴛɪᴍᴇ!"
        )));
        inv.setStack(15, createButton(Items.NETHERITE_SWORD, "§5§lBlackDrdonutSMP §c§lᴛᴏᴘ ᴋɪʟʟs", killLore));

        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, playerInv, p) ->
            new GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X3, syncId, playerInv, inv, 3) {
                @Override
                public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity clicker) {
                }
            }, Text.literal("§5§lBlackDrdonutSMP §8» §6ʟᴇᴀᴅᴇʀʙᴏᴀʀᴅ")));
    }

    private void updateSidebarAndBossBar(MinecraftServer server) {
        // 1. Update Sidebar Leaderboard titled BlackDrdonutSMP
        ServerScoreboard sb = server.getScoreboard();
        ScoreboardObjective old = sb.getNullableObjective("bdd_lb");
        if (old != null) sb.removeObjective(old);

        ScoreboardObjective obj = sb.addObjective(
            "bdd_lb",
            ScoreboardCriterion.DUMMY,
            Text.literal("§5§lBlack§d§lDrdonut§f§lSMP"),
            ScoreboardCriterion.RenderType.INTEGER,
            true,
            null
        );
        sb.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, obj);

        List<PlayerStats> richest = new ArrayList<>(data.players.values());
        richest.sort((a, b) -> Long.compare(b.money, a.money));

        sb.getOrCreateScore(ScoreHolder.fromName("§7§m----------------"), obj).setScore(15);
        sb.getOrCreateScore(ScoreHolder.fromName("§6§lᴛᴏᴘ ʀɪᴄʜᴇsᴛ:"), obj).setScore(14);
        int scoreLine = 13;
        for (int i = 0; i < Math.min(5, richest.size()); i++) {
            PlayerStats ps = richest.get(i);
            String line = "§e#" + (i + 1) + " §f" + ps.name + " §a$" + formatCompact(ps.money);
            if (line.length() > 38) line = line.substring(0, 38);
            sb.getOrCreateScore(ScoreHolder.fromName(line), obj).setScore(scoreLine--);
        }
        sb.getOrCreateScore(ScoreHolder.fromName("§8§m----------------"), obj).setScore(1);
        sb.getOrCreateScore(ScoreHolder.fromName("§d/order §8| §a/shop §8| §b/ah"), obj).setScore(0);

        // 2. Update Per-Player BossBar with Money, Shards, Kills/Deaths & Combat Timer
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            PlayerStats st = getStats(player);
            st.name = player.getName().getString();
            ServerBossBar bar = playerBossBars.computeIfAbsent(player.getUuid(), u -> {
                ServerBossBar b = new ServerBossBar(Text.literal(SERVER_TITLE), BossBar.Color.PURPLE, BossBar.Style.NOTCHED_10);
                b.addPlayer(player);
                return b;
            });
            if (!bar.getPlayers().contains(player)) bar.addPlayer(player);

            int combat = combatTimers.getOrDefault(player.getUuid(), 0);
            String combatText = combat > 0 ? " §8| §c§l⚔ ᴄᴏᴍʙᴀᴛ: " + combat + "s" : "";
            bar.setName(Text.literal(
                "§5§lBlack§d§lDrdonut§f§lSMP §8» §a$" + formatNum(st.money) +
                " §8| §d✦ " + st.shards +
                " §8| §c⚔ " + st.kills + "/" + st.deaths + combatText
            ));
        }
    }

    private void updateHologramLeaderboard(MinecraftServer server) {
        if (data.lbX == null) return;
        ServerWorld world = server.getOverworld();

        for (Entity entity : world.iterateEntities()) {
            if (entity.getCommandTags().contains("blackdrdonut_lb")) {
                entity.discard();
            }
        }

        List<PlayerStats> richest = new ArrayList<>(data.players.values());
        richest.sort((a, b) -> Long.compare(b.money, a.money));

        List<String> lines = new ArrayList<>();
        lines.add("§8§l« §5§lBlack§d§lDrdonut§f§lSMP §6§lʟᴇᴀᴅᴇʀʙᴏᴀʀᴅ §8§l»");
        lines.add("§7§oTop Richest Players on the Server");
        for (int i = 0; i < Math.min(5, richest.size()); i++) {
            PlayerStats ps = richest.get(i);
            lines.add("§e§l#" + (i + 1) + " §f" + ps.name + " §7- §a$" + formatNum(ps.money));
        }

        double yOffset = data.lbY + (lines.size() * 0.3);
        for (String line : lines) {
            ArmorStandEntity stand = new ArmorStandEntity(world, data.lbX, yOffset, data.lbZ);
            stand.setInvisible(true);
            stand.setNoGravity(true);
            stand.setInvulnerable(true);
            stand.setCustomName(Text.literal(line));
            stand.setCustomNameVisible(true);
            stand.addCommandTag("blackdrdonut_lb");
            world.spawnEntity(stand);
            yOffset -= 0.3;
        }
    }

    // =========================================================================
    // 7. MULTI-REGION /RTP GUI
    // =========================================================================
    private void openRtpGui(ServerPlayerEntity player) {
        SimpleInventory inv = new SimpleInventory(27);
        inv.setStack(11, createButton(Items.LIME_CONCRETE, "§a§lɴᴀ ᴇᴀsᴛ", List.of("§7ᴄʟɪᴄᴋ ᴛᴏ ʀᴀɴᴅᴏᴍ ᴛᴇʟᴇᴘᴏʀᴛ!")));
        inv.setStack(12, createButton(Items.GREEN_CONCRETE, "§2§lɴᴀ ᴡᴇsᴛ", List.of("§7ᴄʟɪᴄᴋ ᴛᴏ ʀᴀɴᴅᴏᴍ ᴛᴇʟᴇᴘᴏʀᴛ!")));
        inv.setStack(13, createButton(Items.LIGHT_BLUE_CONCRETE, "§b§lᴇᴜʀᴏᴘᴇ", List.of("§7ᴄʟɪᴄᴋ ᴛᴏ ʀᴀɴᴅᴏᴍ ᴛᴇʟᴇᴘᴏʀᴛ!")));
        inv.setStack(14, createButton(Items.YELLOW_CONCRETE, "§e§lᴀsɪᴀ", List.of("§7ᴄʟɪᴄᴋ ᴛᴏ ʀᴀɴᴅᴏᴍ ᴛᴇʟᴇᴘᴏʀᴛ!")));
        inv.setStack(15, createButton(Items.PURPLE_CONCRETE, "§d§lᴏᴄᴇᴀɴɪᴀ", List.of("§7ᴄʟɪᴄᴋ ᴛᴏ ʀᴀɴᴅᴏᴍ ᴛᴇʟᴇᴘᴏʀᴛ!")));

        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, playerInv, p) ->
            new GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X3, syncId, playerInv, inv, 3) {
                @Override
                public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity clicker) {
                    if (slotIndex >= 11 && slotIndex <= 15) {
                        player.closeHandledScreen();
                        performRtp(player);
                    }
                }
            }, Text.literal("§5§lBlackDrdonutSMP §8» §aʀᴛᴘ ʀᴇɢɪᴏɴs")));
    }

    private void performRtp(ServerPlayerEntity player) {
        ServerWorld world = player.getServer().getOverworld();
        Random rand = new Random();
        int x = rand.nextInt(10000) - 5000;
        int z = rand.nextInt(10000) - 5000;
        world.getChunk(x >> 4, z >> 4);
        int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z) + 1;
        player.teleport(world, x + 0.5, y, z + 0.5, player.getYaw(), player.getPitch());
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 100, 0, false, false));
        player.sendMessage(Text.literal("§a§l✔ §aʀᴀɴᴅᴏᴍʟʏ ᴛᴇʟᴇᴘᴏʀᴛᴇᴅ ᴛᴏ §f" + x + ", " + y + ", " + z), false);
        playSound(player, true);
    }

    // =========================================================================
    // 8. EVENTS (COMBAT TAG, KILLS/DEATHS, TIMERS)
    // =========================================================================
    private void registerEvents() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity p = handler.getPlayer();
            getStats(p).name = p.getName().getString();
            saveData();
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity p = handler.getPlayer();
            if (combatTimers.getOrDefault(p.getUuid(), 0) > 0) {
                p.kill();
                server.getPlayerManager().broadcast(Text.literal("§c§l⚔ §f" + p.getName().getString() + " §cᴄᴏᴍʙᴀᴛ ʟᴏɢɢᴇᴅ ᴀɴᴅ ᴅɪᴇᴅ!"), false);
            }
            combatTimers.remove(p.getUuid());
            afkPlayers.remove(p.getUuid());
        });

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayerEntity victim && source.getAttacker() instanceof ServerPlayerEntity attacker) {
                if (!victim.equals(attacker)) {
                    combatTimers.put(victim.getUuid(), 15);
                    combatTimers.put(attacker.getUuid(), 15);
                }
            }
            return true;
        });

        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((world, entity, killedEntity) -> {
            if (entity instanceof ServerPlayerEntity killer && killedEntity instanceof ServerPlayerEntity victim) {
                getStats(killer).kills++;
                getStats(killer).money += 500;
                getStats(victim).deaths++;
                combatTimers.remove(victim.getUuid());
                saveData();
                killer.sendMessage(Text.literal("§a§l⚔ §aʏᴏᴜ ᴋɪʟʟᴇᴅ §f" + victim.getName().getString() + " §a(+§2$500§a)"), false);
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickCounter++;
            if (tickCounter % 20 == 0) { // Every 1 second
                for (UUID uuid : new HashSet<>(combatTimers.keySet())) {
                    int left = combatTimers.get(uuid) - 1;
                    if (left <= 0) {
                        combatTimers.remove(uuid);
                    } else {
                        combatTimers.put(uuid, left);
                    }
                }
                updateSidebarAndBossBar(server);
            }
            if (tickCounter % 100 == 0) { // Every 5 seconds
                updateHologramLeaderboard(server);
            }
            if (tickCounter % 1200 == 0) { // Every 60 seconds: AFK Shards
                for (UUID uuid : afkPlayers) {
                    ServerPlayerEntity p = server.getPlayerManager().getPlayer(uuid);
                    if (p != null) {
                        getStats(p).shards += 1;
                        p.sendMessage(Text.literal("§d§l✦ §d+1 sʜᴀʀᴅ §7(ᴀғᴋ ʀᴇᴡᴀʀᴅ)"), true);
                    }
                }
                saveData();
            }
        });
    }

    // =========================================================================
    // 9. HELPER FUNCTIONS
    // =========================================================================
    private boolean isInCombat(ServerPlayerEntity player) {
        int timer = combatTimers.getOrDefault(player.getUuid(), 0);
        if (timer > 0) {
            player.sendMessage(Text.literal("§c§l⚔ §cʏᴏᴜ ᴀʀᴇ ɪɴ ᴄᴏᴍʙᴀᴛ ғᴏʀ §e" + timer + "s§c!"), false);
            return true;
        }
        return false;
    }

    private PlayerStats getStats(ServerPlayerEntity player) {
        return data.players.computeIfAbsent(player.getUuidAsString(), k -> {
            PlayerStats ps = new PlayerStats();
            ps.name = player.getName().getString();
            return ps;
        });
    }

    private ItemStack createButton(Item item, String title, List<String> lore) {
        ItemStack stack = new ItemStack(item);
        setItemMeta(stack, title, lore);
        return stack;
    }

    private void setItemMeta(ItemStack stack, String title, List<String> loreLines) {
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(title));
        List<Text> textLore = loreLines.stream().map(Text::literal).collect(Collectors.toList());
        stack.set(DataComponentTypes.LORE, new LoreComponent(textLore));
    }

    private int countItemInInventory(ServerPlayerEntity player, Item item) {
        int count = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack s = player.getInventory().getStack(i);
            if (s.getItem() == item) count += s.getCount();
        }
        return count;
    }

    private void removeItemsFromInventory(ServerPlayerEntity player, Item item, int amount) {
        int left = amount;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack s = player.getInventory().getStack(i);
            if (s.getItem() == item) {
                int take = Math.min(left, s.getCount());
                s.decrement(take);
                left -= take;
                if (left <= 0) break;
            }
        }
    }

    private void playSound(ServerPlayerEntity player, boolean success) {
        player.getServerWorld().playSound(
            null, player.getBlockPos(),
            success ? SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP : SoundEvents.ENTITY_VILLAGER_NO,
            SoundCategory.PLAYERS, 1.0f, 1.0f
        );
    }

    private String formatNum(long num) {
        return NumberFormat.getInstance(Locale.US).format(num);
    }

    private String formatCompact(long num) {
        if (num >= 1_000_000_000L) return String.format("%.1fB", num / 1_000_000_000.0);
        if (num >= 1_000_000L) return String.format("%.1fM", num / 1_000_000.0);
        if (num >= 1_000L) return String.format("%.1fK", num / 1_000.0);
        return String.valueOf(num);
    }

    private void loadData() {
        try {
            dataFile = FabricLoader.getInstance().getConfigDir().resolve("blackdrdonutsmp_data.json").toFile();
            if (dataFile.exists()) {
                try (FileReader reader = new FileReader(dataFile)) {
                    SaveData loaded = GSON.fromJson(reader, SaveData.class);
                    if (loaded != null) data = loaded;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveData() {
        try {
            if (dataFile == null) {
                dataFile = FabricLoader.getInstance().getConfigDir().resolve("blackdrdonutsmp_data.json").toFile();
            }
            if (!dataFile.getParentFile().exists()) dataFile.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(dataFile)) {
                GSON.toJson(data, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
