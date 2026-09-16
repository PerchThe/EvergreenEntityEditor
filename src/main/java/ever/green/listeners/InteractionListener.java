package ever.green.listeners;

import ever.green.EntityEditor;
import ever.green.managers.EditorSessionManager;
import ever.green.managers.LanguageManager;
import ever.green.utils.ColorUtil;
import ever.green.utils.EditorState;
import ever.green.utils.ItemUtil;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.entity.memory.MemoryKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.EulerAngle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class InteractionListener implements Listener {

    private final EntityEditor plugin;
    private final EditorSessionManager sessionManager;
    private final LanguageManager lang;

    private final Map<UUID, PoseClipboard[]> clipboards = new HashMap<>();
    private final Map<UUID, Long> lastInteract = new HashMap<>();
    private final Map<UUID, Long> lastAiWarning = new HashMap<>();

    public InteractionListener(EntityEditor plugin) {
        this.plugin = plugin;
        this.sessionManager = plugin.getSessionManager();
        this.lang = plugin.getLang();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        clipboards.remove(uuid);
        lastInteract.remove(uuid);
        lastAiWarning.remove(uuid);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityRightClick(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();

        if (sessionManager.isInEditorMode(player)) {
            event.setCancelled(true);
        }

        if (event.getRightClicked() instanceof ArmorStand) return;

        Entity clicked = event.getRightClicked();

        if (clicked instanceof Villager villager) {

            // --- AUTOMATIC LEGACY MIGRATION ---
            migrateLegacyData(villager);

            // --- BONK NAMETAG FEATURE ---
            if (!sessionManager.isInEditorMode(player) && event.getHand() == EquipmentSlot.HAND) {
                ItemStack handItem = player.getInventory().getItemInMainHand();
                if (handItem.getType() == Material.NAME_TAG && handItem.hasItemMeta() && handItem.getItemMeta().hasDisplayName()) {
                    String plainName = PlainTextComponentSerializer.plainText().serialize(handItem.getItemMeta().displayName());
                    plainName = plainName.replaceAll("^\\[|\\]$", "").trim();

                    if (plainName.equalsIgnoreCase("Bonk")) {
                        event.setCancelled(true);
                        villager.setAI(false);
                        villager.setAware(false);
                        villager.customName(handItem.getItemMeta().displayName());
                        villager.setCustomNameVisible(false);
                        player.sendActionBar(ColorUtil.parse("<#48ab76>Villager Bonked! (AI Frozen)"));
                        player.playSound(villager.getLocation(), org.bukkit.Sound.BLOCK_ANVIL_LAND, 0.5f, 1.5f);
                        return;
                    } else if (!villager.hasAI()) {
                        event.setCancelled(true);
                        villager.setAI(true);
                        villager.setAware(true);

                        // SMART MEMORY FIX: Only wipe memories if the workstation is actually broken.
                        // This prevents their trades from resetting if you just bonk/un-bonk them.
                        Location jobSite = villager.getMemory(MemoryKey.JOB_SITE);
                        if (jobSite != null && jobSite.getBlock().getType().isAir()) {
                            villager.setMemory(MemoryKey.JOB_SITE, null);
                            villager.setMemory(MemoryKey.POTENTIAL_JOB_SITE, null);
                        }

                        villager.customName(handItem.getItemMeta().displayName());
                        villager.setCustomNameVisible(false);
                        player.sendActionBar(ColorUtil.parse("<yellow>Villager Un-Bonked! (AI Restored)"));
                        player.playSound(villager.getLocation(), org.bukkit.Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 0.5f, 1.5f);
                        return;
                    }
                }
            }

            long currentTime = System.currentTimeMillis() / 1000;
            long cooldown = getLevelUpCooldown(villager);

            if (cooldown > currentTime) {
                event.setCancelled(true);
                long secLeft = cooldown - currentTime;
                player.sendActionBar(lang.getMessage("actionbar.levelup_cooldown", "%sec%", String.valueOf(secLeft)));
                villager.shakeHead();
                return;
            }

            if (!villager.hasAI() || !villager.isAware()) {
                if (villager.hasAI()) {
                    villager.setAI(false);
                }
                handleVillagerRestock(player, villager);
            }
        }

        if (!sessionManager.isInEditorMode(player)) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        routeInteraction(player, clicked, false);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onArmorStandRightClick(PlayerInteractAtEntityEvent event) {
        Player player = event.getPlayer();
        if (!sessionManager.isInEditorMode(player)) return;
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) return;

        routeInteraction(player, event.getRightClicked(), false);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!sessionManager.isInEditorMode(player)) return;
        event.setCancelled(true);

        routeInteraction(player, event.getEntity(), true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onToolUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!sessionManager.isInEditorMode(player)) return;

        if (event.getHand() == EquipmentSlot.OFF_HAND) return;
        if (event.getAction() == Action.PHYSICAL) return;

        event.setCancelled(true);

        boolean isLeftClick = (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK);

        ItemStack item = player.getInventory().getItemInMainHand();
        String toolId = getToolId(item);
        if (toolId == null) return;

        if ("selector".equals(toolId)) {
            sessionManager.setSelection(player, null, false);
            sessionManager.changeState(player, EditorState.EMPTY_PAGE);
        } else if ("place_stand".equals(toolId)) {
            if (!isLeftClick && event.getClickedBlock() != null) {

                if (!sessionManager.hasSurvivalItem(player, Material.ARMOR_STAND)) {
                    player.sendActionBar(ColorUtil.parse("<red>You don't have any Armor Stands left!"));
                    return;
                }

                Location loc = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation().add(0.5, 0, 0.5);

                String editError = getEditError(player, loc);
                if (editError != null) {
                    player.sendActionBar(lang.getMessage(editError));
                    return;
                }

                float yaw = Math.round(player.getLocation().getYaw() / 45f) * 45f + 180f;
                loc.setYaw(yaw);

                ArmorStand stand = player.getWorld().spawn(loc, ArmorStand.class);
                sessionManager.consumeSurvivalItem(player, Material.ARMOR_STAND);
                sessionManager.setSelection(player, stand, false);
                player.playSound(loc, org.bukkit.Sound.ENTITY_ARMOR_STAND_PLACE, 1f, 1f);

                sessionManager.changeState(player, EditorState.ARMOR_STAND_PAGE_1);
            }
        } else {
            handleToolUse(player, toolId, isLeftClick, player.isSneaking());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getType() != InventoryType.MERCHANT) return;
        if (!(event.getInventory().getHolder() instanceof Villager villager)) return;
        if (villager.hasAI() && villager.isAware()) return;

        int currentLevel = villager.getVillagerLevel();
        int projectedLevel = getProjectedLevel(villager);

        if (currentLevel < projectedLevel) {
            long cooldownSeconds = 5;
            setLevelUpCooldown(villager, (System.currentTimeMillis() / 1000) + cooldownSeconds);

            villager.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, (int) (cooldownSeconds * 20), 120, false, false));
            villager.setAI(true);
            villager.setAware(true);

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (villager.isValid()) {
                    villager.setAI(false);
                    villager.setAware(false);
                }
            }, cooldownSeconds * 20L);
        }
    }

    private void routeInteraction(Player player, Entity clicked, boolean isLeftClick) {
        ItemStack item = player.getInventory().getItemInMainHand();
        String toolId = getToolId(item);
        if (toolId == null) return;

        if (isBlacklisted(clicked)) {
            player.sendActionBar(lang.getMessage("actionbar.blacklisted"));
            return;
        }

        String editError = getEditError(player, clicked.getLocation());
        if (editError == null) {
            Entity currentSelection = sessionManager.getSelection(player);

            if ("selector".equals(toolId) && currentSelection != null && currentSelection.equals(clicked)) {
                sessionManager.setSelection(player, null, false);
                sessionManager.changeState(player, EditorState.EMPTY_PAGE);
                return;
            }

            if (currentSelection == null || !currentSelection.equals(clicked)) {
                EditorState oldState = sessionManager.getState(player);
                sessionManager.setSelection(player, clicked, false);

                if (currentSelection != null && currentSelection.getType() == clicked.getType() && oldState != null) {
                    sessionManager.changeState(player, oldState);
                } else {
                    EditorState newState = (clicked instanceof ArmorStand) ? EditorState.ARMOR_STAND_PAGE_1 : EditorState.MOB_PAGE_1;
                    sessionManager.changeState(player, newState);
                }
            }
        } else {
            player.sendActionBar(lang.getMessage(editError));
            return;
        }

        if ("selector".equals(toolId) || "place_stand".equals(toolId)) return;

        handleToolUse(player, toolId, isLeftClick, player.isSneaking());
    }

    private void handleToolUse(Player player, String toolId, boolean isLeftClick, boolean isSneaking) {
        long now = System.currentTimeMillis();
        if (now - lastInteract.getOrDefault(player.getUniqueId(), 0L) < 50) return;
        lastInteract.put(player.getUniqueId(), now);

        if ("exit".equals(toolId)) {
            sessionManager.exitEditorMode(player);
            return;
        }

        if ("next_page".equals(toolId)) {
            EditorState current = sessionManager.getState(player);
            sessionManager.changeState(player, isLeftClick ? current.getPreviousPage() : current.getNextPage());
            player.playSound(player.getLocation(), org.bukkit.Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f);
            return;
        }

        Entity target = sessionManager.getSelection(player);
        if (target == null) {
            player.sendActionBar(lang.getMessage("actionbar.select_first"));
            return;
        }

        if (!target.getWorld().equals(player.getWorld()) || target.getLocation().distanceSquared(player.getLocation()) > 225) {
            player.sendActionBar(ColorUtil.parse("<red>Entity is too far away to edit!"));
            sessionManager.setSelection(player, null, false);
            sessionManager.changeState(player, EditorState.EMPTY_PAGE);
            return;
        }

        String editError = getEditError(player, target.getLocation());
        if (editError != null) {
            player.sendActionBar(lang.getMessage(editError));
            return;
        }

        processToolLogic(player, target, toolId, isLeftClick, isSneaking);
    }

    private void processToolLogic(Player player, Entity target, String toolId, boolean isLeftClick, boolean isSneaking) {

        double moveDist = isSneaking ? 0.1 : 1.0;
        float rotAngle = isSneaking ? 1.0f : 10.0f;
        double scaleStep = isSneaking ? 0.1 : 0.5;

        int mult = isLeftClick ? -1 : 1;
        moveDist *= mult;
        rotAngle *= mult;
        scaleStep *= mult;

        Location loc = target.getLocation().clone();

        boolean isMobWithAi = target instanceof LivingEntity && !(target instanceof ArmorStand) && ((LivingEntity) target).hasAI();

        if (toolId.equals("rotate_yaw") || toolId.equals("rotate_pitch")) {
            if (isMobWithAi) {
                player.sendActionBar(lang.getMessage("actionbar.ai_blocks_rotation"));
                return;
            }
        }

        if (toolId.equals("move_y") && isMobWithAi) {
            long now = System.currentTimeMillis();
            if (now - lastAiWarning.getOrDefault(player.getUniqueId(), 0L) > 10000) {
                player.sendMessage(lang.getMessage("messages.ai_warning"));
                lastAiWarning.put(player.getUniqueId(), now);
            }
        }

        switch (toolId) {
            case "move_x" -> loc.add(moveDist, 0, 0);
            case "move_y" -> loc.add(0, moveDist, 0);
            case "move_z" -> loc.add(0, 0, moveDist);

            case "rotate_global", "rotate_yaw" -> loc.setYaw(loc.getYaw() + rotAngle);
            case "rotate_pitch" -> loc.setPitch(loc.getPitch() + rotAngle);

            case "scale" -> {
                if (target instanceof ArmorStand stand) {
                    if (stand.getAttribute(Attribute.SCALE) != null) {
                        double currentScale = stand.getAttribute(Attribute.SCALE).getBaseValue();
                        double newScale = Math.max(0.1, Math.min(10.0, currentScale + scaleStep));
                        stand.getAttribute(Attribute.SCALE).setBaseValue(newScale);
                        player.sendActionBar(lang.getMessage("actionbar.scale", "%scale%", String.format("%.1f", newScale)));
                    }
                }
                return;
            }

            case "toggle_ai" -> {
                if (target instanceof Creature creature) {
                    if (creature.getTarget() != null && creature.getTarget().equals(player)) {
                        player.sendActionBar(lang.getMessage("actionbar.combat_lock"));
                        player.playSound(target.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                        return;
                    }
                }

                if (target instanceof LivingEntity living) {
                    boolean currentlyHasAI = living.hasAI();
                    boolean newAI = !currentlyHasAI;
                    living.setAI(newAI);

                    if (!(living instanceof ArmorStand)) {
                        living.setInvulnerable(!newAI);
                    }

                    if (currentlyHasAI && living instanceof Monster) {
                        player.playSound(target.getLocation(), org.bukkit.Sound.ENTITY_ZOMBIE_INFECT, 0.5f, 0.5f);
                        if (living instanceof Creature creature) {
                            creature.setTarget(null);
                        }
                    }

                    player.sendActionBar(lang.getMessage("actionbar.ai", "%state%", (!newAI) ? "Frozen" : "Unfrozen"));
                }
                return;
            }
            case "toggle_invuln" -> {
                if (target instanceof ArmorStand stand) {
                    stand.setInvulnerable(!stand.isInvulnerable());
                    player.sendActionBar(lang.getMessage("actionbar.invulnerable", "%state%", String.valueOf(stand.isInvulnerable())));
                } else if (target instanceof LivingEntity) {
                    player.sendActionBar(ColorUtil.parse("<red>Invulnerability is automatically managed by the AI toggle for mobs."));
                } else {
                    target.setInvulnerable(!target.isInvulnerable());
                    player.sendActionBar(lang.getMessage("actionbar.invulnerable", "%state%", String.valueOf(target.isInvulnerable())));
                }
                return;
            }
            case "toggle_glow" -> {
                target.setGlowing(!target.isGlowing());
                player.sendActionBar(lang.getMessage("actionbar.glowing", "%state%", String.valueOf(target.isGlowing())));
                return;
            }
            case "toggle_silence" -> {
                target.setSilent(!target.isSilent());
                player.sendActionBar(lang.getMessage("actionbar.silenced", "%state%", String.valueOf(target.isSilent())));
                return;
            }
            case "toggle_age" -> {
                if (target instanceof Ageable ageable) {
                    if (ageable.isAdult()) ageable.setBaby();
                    else ageable.setAdult();
                    player.sendActionBar(lang.getMessage("actionbar.age_toggled"));
                }
                else if (target instanceof Zombie zombie) {
                    zombie.setBaby(!zombie.isBaby());
                    player.sendActionBar(lang.getMessage("actionbar.age_toggled"));
                }
                else if (target instanceof org.bukkit.entity.PiglinAbstract piglin) {
                    piglin.setBaby(!piglin.isBaby());
                    player.sendActionBar(lang.getMessage("actionbar.age_toggled"));
                }
                else if (target instanceof Slime slime) {
                    slime.setSize(slime.getSize() == 1 ? 2 : 1);
                    player.sendActionBar(lang.getMessage("actionbar.age_toggled"));
                }
                return;
            }
            case "toggle_age_lock" -> {
                if (target instanceof Ageable ageable) {
                    boolean isLocked = ageable.getAgeLock();
                    ageable.setAgeLock(!isLocked);
                    player.sendActionBar(ColorUtil.parse(!isLocked ? "<green>Age locked! Entity will not grow up." : "<yellow>Age unlocked! Entity will grow naturally."));
                } else {
                    player.sendActionBar(ColorUtil.parse("<red>This entity does not age naturally."));
                }
                return;
            }
            case "untame" -> {
                if (target instanceof Tameable tameable) {
                    NamespacedKey prevOwnerKey = new NamespacedKey(plugin, "pvo_previous_owner");

                    if (tameable.isTamed()) {
                        if (tameable.getOwner() != null && tameable.getOwner().getUniqueId().equals(player.getUniqueId())) {

                            tameable.getPersistentDataContainer().set(prevOwnerKey, PersistentDataType.STRING, player.getUniqueId().toString());

                            tameable.setOwner(null);
                            tameable.setTamed(false);

                            if (target instanceof org.bukkit.entity.Sittable sittable) {
                                sittable.setSitting(false);
                            }
                            if (target instanceof Wolf wolf) {
                                wolf.setAngry(false);
                            }

                            player.spawnParticle(org.bukkit.Particle.CLOUD, loc.add(0, 1, 0), 10, 0.2, 0.2, 0.2, 0.05);
                            player.sendActionBar(ColorUtil.parse("<yellow>Entity untamed."));
                        } else {
                            player.sendActionBar(ColorUtil.parse("<red>You can only untame animals tamed by you."));
                            player.playSound(loc, org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                        }
                    } else {
                        String prevOwner = tameable.getPersistentDataContainer().get(prevOwnerKey, PersistentDataType.STRING);

                        if (prevOwner != null && prevOwner.equals(player.getUniqueId().toString())) {
                            tameable.setTamed(true);
                            tameable.setOwner(player);
                            player.spawnParticle(org.bukkit.Particle.HEART, loc.add(0, 1, 0), 5, 0.3, 0.3, 0.3, 0.05);
                            player.playSound(loc, org.bukkit.Sound.ENTITY_GENERIC_EAT, 1f, 1f);
                            player.sendActionBar(ColorUtil.parse("<green>Entity re-tamed!"));
                        } else {
                            player.sendActionBar(ColorUtil.parse("<red>You can only re-tame animals you previously owned."));
                            player.playSound(loc, org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                        }
                    }
                }
                return;
            }

            case "toggle_arms" -> {
                if (target instanceof ArmorStand stand) {
                    stand.setArms(!stand.hasArms());
                    player.sendActionBar(lang.getMessage("actionbar.arms", "%state%", String.valueOf(stand.hasArms())));
                }
                return;
            }
            case "toggle_baseplate" -> {
                if (target instanceof ArmorStand stand) {
                    stand.setBasePlate(!stand.hasBasePlate());
                    player.sendActionBar(lang.getMessage("actionbar.baseplate", "%state%", String.valueOf(stand.hasBasePlate())));
                }
                return;
            }
            case "toggle_visibility" -> {
                if (target instanceof ArmorStand stand) {
                    stand.setVisible(!stand.isVisible());
                    player.sendActionBar(lang.getMessage("actionbar.visibility", "%state%", String.valueOf(stand.isVisible())));
                }
                return;
            }

            case "toggle_lock" -> {
                if (target instanceof ArmorStand stand) {
                    boolean isLocked = stand.hasEquipmentLock(EquipmentSlot.HAND, ArmorStand.LockType.ADDING_OR_CHANGING);
                    for (EquipmentSlot slot : EquipmentSlot.values()) {
                        if (isLocked) {
                            stand.removeEquipmentLock(slot, ArmorStand.LockType.ADDING_OR_CHANGING);
                            stand.removeEquipmentLock(slot, ArmorStand.LockType.REMOVING_OR_CHANGING);
                        } else {
                            stand.addEquipmentLock(slot, ArmorStand.LockType.ADDING_OR_CHANGING);
                            stand.addEquipmentLock(slot, ArmorStand.LockType.REMOVING_OR_CHANGING);
                        }
                    }
                    player.sendActionBar(lang.getMessage("actionbar.locked", "%state%", String.valueOf(!isLocked)));
                }
                return;
            }

            case "edit_name" -> {
                sessionManager.exitEditorMode(player);

                Dialog dialog = Dialog.create(builder -> builder.empty()
                        .base(DialogBase.builder(Component.text("Edit Entity Name", TextColor.color(0x48ab76)))
                                .canCloseWithEscape(false)
                                .inputs(List.of(
                                        DialogInput.text("entity_name", Component.text("New Name (Leave blank to clear)"))
                                                .build()
                                ))
                                .build()
                        )
                        .type(DialogType.confirmation(
                                ActionButton.builder(Component.text("Save", NamedTextColor.GREEN))
                                        .action(DialogAction.customClick(
                                                (view, audience) -> {
                                                    if (!(audience instanceof Player p)) return;

                                                    String text = view.getText("entity_name");

                                                    if (text != null && !text.equalsIgnoreCase("cancel")) {
                                                        if (text.isBlank() || text.equalsIgnoreCase("clear")) {
                                                            target.setCustomNameVisible(false);
                                                            target.customName(null);
                                                            p.playSound(target.getLocation(), org.bukkit.Sound.BLOCK_GRINDSTONE_USE, 1f, 1f);
                                                            p.sendActionBar(ColorUtil.parse("<yellow>Name cleared."));

                                                            if (target instanceof Villager v && !v.hasAI()) {
                                                                v.setAI(true);
                                                                v.setAware(true);
                                                            }
                                                        } else {
                                                            target.customName(ColorUtil.parse(text));
                                                            // FIX: ArmorStands show through walls (holograms), Mobs act like vanilla tags
                                                            if (target instanceof ArmorStand) {
                                                                target.setCustomNameVisible(true);
                                                            } else {
                                                                target.setCustomNameVisible(false);
                                                            }

                                                            p.playSound(target.getLocation(), org.bukkit.Sound.BLOCK_ANVIL_USE, 1f, 1f);
                                                            p.sendActionBar(lang.getMessage("actionbar.name_updated"));

                                                            if (target instanceof Villager v) {
                                                                if (text.trim().equalsIgnoreCase("Bonk")) {
                                                                    v.setAI(false);
                                                                    v.setAware(false);
                                                                } else if (!v.hasAI()) {
                                                                    v.setAI(true);
                                                                    v.setAware(true);
                                                                }
                                                            }
                                                        }
                                                    }

                                                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                                        if (p.isOnline() && target.isValid()) {
                                                            sessionManager.enterEditorMode(p, target);
                                                            EditorState returnState = (target instanceof ArmorStand) ? EditorState.ARMOR_STAND_PAGE_3 : EditorState.MOB_PAGE_2;
                                                            sessionManager.changeState(p, returnState);
                                                        }
                                                    }, 1L);
                                                },
                                                ClickCallback.Options.builder().uses(1).build()
                                        ))
                                        .build(),
                                ActionButton.builder(Component.text("Cancel", NamedTextColor.RED))
                                        .action(DialogAction.customClick(
                                                (view, audience) -> {
                                                    if (!(audience instanceof Player p)) return;
                                                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                                        if (p.isOnline() && target.isValid()) {
                                                            sessionManager.enterEditorMode(p, target);
                                                            EditorState returnState = (target instanceof ArmorStand) ? EditorState.ARMOR_STAND_PAGE_3 : EditorState.MOB_PAGE_2;
                                                            sessionManager.changeState(p, returnState);
                                                        }
                                                    }, 1L);
                                                },
                                                ClickCallback.Options.builder().uses(1).build()
                                        ))
                                        .build()
                        ))
                );

                player.showDialog(dialog);
                return;
            }

            case "axis_selector" -> {
                sessionManager.cycleAxis(player);
                EditorSessionManager.Axis active = sessionManager.getActiveAxis(player);
                String axisName = active == EditorSessionManager.Axis.X ? "X (Pitch)" : (active == EditorSessionManager.Axis.Y ? "Y (Yaw)" : "Z (Roll)");

                player.sendActionBar(lang.getMessage("actionbar.axis_set", "%axis%", axisName));
                player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1f, 1f);

                sessionManager.changeState(player, sessionManager.getState(player));
                return;
            }

            case "pose_head", "pose_body", "pose_l_arm", "pose_r_arm", "pose_l_leg", "pose_r_leg" -> {
                if (target instanceof ArmorStand stand) {
                    EditorSessionManager.Axis activeAxis = sessionManager.getActiveAxis(player);
                    double radAngle = Math.toRadians(rotAngle);

                    EulerAngle currentAngle = getLimbAngle(stand, toolId);
                    double x = currentAngle.getX();
                    double y = currentAngle.getY();
                    double z = currentAngle.getZ();

                    switch (activeAxis) {
                        case X -> x = (x + radAngle) % (2 * Math.PI);
                        case Y -> y = (y + radAngle) % (2 * Math.PI);
                        case Z -> z = (z + radAngle) % (2 * Math.PI);
                    }

                    if (x < 0) x += 2 * Math.PI;
                    if (y < 0) y += 2 * Math.PI;
                    if (z < 0) z += 2 * Math.PI;

                    setLimbAngle(stand, toolId, new EulerAngle(x, y, z));
                    player.sendActionBar(lang.getMessage("actionbar.pose_adjusted", "%part%", toolId.replace("pose_", ""), "%axis%", activeAxis.name()));
                }
                return;
            }
            case "reset_pose" -> {
                if (target instanceof ArmorStand stand) {
                    EulerAngle zero = new EulerAngle(0, 0, 0);
                    stand.setHeadPose(zero); stand.setBodyPose(zero);
                    stand.setLeftArmPose(zero); stand.setRightArmPose(zero);
                    stand.setLeftLegPose(zero); stand.setRightLegPose(zero);
                    player.sendActionBar(lang.getMessage("actionbar.pose_reset"));
                }
                return;
            }

            case "equipment_gui" -> {
                if (target instanceof ArmorStand stand) {
                    plugin.getGuiManager().openEquipmentGUI(player, stand);
                }
                return;
            }

            case "copy_paste_1", "copy_paste_2", "copy_paste_3" -> {
                if (target instanceof ArmorStand stand) {
                    int slotIndex = toolId.equals("copy_paste_1") ? 0 : (toolId.equals("copy_paste_2") ? 1 : 2);
                    PoseClipboard[] playerBoards = clipboards.computeIfAbsent(player.getUniqueId(), k -> new PoseClipboard[3]);

                    if (isLeftClick) {
                        playerBoards[slotIndex] = new PoseClipboard(stand);
                        player.sendActionBar(lang.getMessage("actionbar.clipboard_copied", "%slot%", String.valueOf(slotIndex + 1)));
                        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 2f);
                    } else {
                        PoseClipboard board = playerBoards[slotIndex];
                        if (board != null) {
                            board.applyTo(stand);
                            player.sendActionBar(lang.getMessage("actionbar.clipboard_pasted", "%slot%", String.valueOf(slotIndex + 1)));
                            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_ANVIL_USE, 0.5f, 2f);
                        } else {
                            player.sendActionBar(lang.getMessage("actionbar.clipboard_empty", "%slot%", String.valueOf(slotIndex + 1)));
                            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                        }
                    }
                }
                return;
            }
        }

        if (toolId.startsWith("move_") || toolId.startsWith("rotate_")) {
            String moveError = getEditError(player, loc);
            if (moveError != null) {
                player.sendActionBar(lang.getMessage(moveError));
                return;
            }
            target.teleport(loc);
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.5f);
        }
    }

    private void handleVillagerRestock(Player player, Villager vil) {
        long[] restockTimes = {2000L, 8000L};
        long worldTick = vil.getWorld().getFullTime();
        long currentDayTick = vil.getWorld().getTime();
        long beginningOfDayTick = worldTick - currentDayTick;
        long lastRestockTick = getLastRestock(vil);

        boolean restocked = false;

        if (player.hasPermission("evergreen.editor.restockbypass") || player.hasPermission("pvo.restockcooldown.bypass")) {
            restocked = true;
        } else {
            for (long restockTime : restockTimes) {
                long todayRestock = beginningOfDayTick + restockTime;
                if (worldTick >= todayRestock && lastRestockTick < todayRestock) {
                    restocked = true;
                    break;
                }
            }
        }

        if (restocked) {
            List<MerchantRecipe> recipes = new ArrayList<>();
            for (MerchantRecipe recipe : vil.getRecipes()) {
                recipe.setUses(0);
                recipes.add(recipe);
            }
            vil.setRecipes(recipes);
            setLastRestock(vil, worldTick);

            if (player.hasPermission("evergreen.editor.restockbypass") || player.hasPermission("pvo.restockcooldown.bypass")) {
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.5f);
            }
        } else {
            long timeTillNextRestock = Long.MAX_VALUE;
            for (long restockTime : restockTimes) {
                long restockTick = beginningOfDayTick + restockTime;
                if (worldTick < restockTick) {
                    timeTillNextRestock = Math.min(timeTillNextRestock, restockTick - worldTick);
                }
            }

            if (timeTillNextRestock == Long.MAX_VALUE) {
                timeTillNextRestock = (24000 + beginningOfDayTick + restockTimes[0]) - worldTick;
            }

            long totalSeconds = timeTillNextRestock / 20;
            long min = totalSeconds / 60;
            long sec = totalSeconds % 60;

            player.sendActionBar(lang.getMessage("actionbar.next_restock", "%min%", String.valueOf(min), "%sec%", String.valueOf(sec)));
        }
    }

    private void migrateLegacyData(Villager vil) {
        PersistentDataContainer container = vil.getPersistentDataContainer();

        try {
            NamespacedKey oldMarkerKey = NamespacedKey.fromString("villageroptimisation:marker");
            if (oldMarkerKey != null && container.has(oldMarkerKey, PersistentDataType.BOOLEAN)) {
                boolean isEnabled = container.getOrDefault(oldMarkerKey, PersistentDataType.BOOLEAN, true);
                if (!isEnabled) {
                    vil.setAI(false);
                    vil.setAware(false);
                }
                container.remove(oldMarkerKey);
            }

            NamespacedKey oldTimeKey = NamespacedKey.fromString("villageroptimisation:time");
            if (oldTimeKey != null && container.has(oldTimeKey, PersistentDataType.LONG)) {
                long oldTime = container.getOrDefault(oldTimeKey, PersistentDataType.LONG, 0L);
                setLastRestock(vil, oldTime);
                container.remove(oldTimeKey);
            }

            NamespacedKey oldLevelKey = NamespacedKey.fromString("villageroptimisation:levelcooldown");
            if (oldLevelKey != null && container.has(oldLevelKey, PersistentDataType.LONG)) {
                long oldLevelCooldown = container.getOrDefault(oldLevelKey, PersistentDataType.LONG, 0L);
                setLevelUpCooldown(vil, oldLevelCooldown);
                container.remove(oldLevelKey);
            }

            NamespacedKey oldAiKey = NamespacedKey.fromString("villageroptimisation:cooldown");
            if (oldAiKey != null && container.has(oldAiKey, PersistentDataType.LONG)) {
                container.remove(oldAiKey);
            }
        } catch (Exception ignored) {}
    }

    private long getLastRestock(Villager v) {
        PersistentDataContainer container = v.getPersistentDataContainer();
        NamespacedKey newKey = new NamespacedKey(plugin, "pvo_last_restock");

        return container.getOrDefault(newKey, PersistentDataType.LONG, 0L);
    }

    private void setLastRestock(Villager v, long time) {
        PersistentDataContainer container = v.getPersistentDataContainer();
        container.set(new NamespacedKey(plugin, "pvo_last_restock"), PersistentDataType.LONG, time);
    }

    private long getLevelUpCooldown(Villager v) {
        PersistentDataContainer container = v.getPersistentDataContainer();
        NamespacedKey newKey = new NamespacedKey(plugin, "pvo_levelup_cooldown");

        return container.getOrDefault(newKey, PersistentDataType.LONG, 0L);
    }

    private void setLevelUpCooldown(Villager v, long time) {
        PersistentDataContainer container = v.getPersistentDataContainer();
        container.set(new NamespacedKey(plugin, "pvo_levelup_cooldown"), PersistentDataType.LONG, time);
    }

    private int getProjectedLevel(Villager v) {
        int exp = v.getVillagerExperience();
        if (exp >= 250) return 5;
        if (exp >= 150) return 4;
        if (exp >= 70) return 3;
        if (exp >= 10) return 2;
        return 1;
    }

    private String getToolId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(ItemUtil.EDITOR_KEY, PersistentDataType.STRING);
    }

    private boolean isBlacklisted(Entity target) {
        return target instanceof Player || target instanceof EnderDragon || target instanceof Wither || target instanceof Giant;
    }

    private String getEditError(Player player, Location loc) {
        if (Bukkit.getPluginManager().getPlugin("GriefPrevention") == null) return null;

        Claim claim = GriefPrevention.instance.dataStore.getClaimAt(loc, false, null);

        if (claim == null) {
            return "actionbar.not_in_claim";
        }

        if (claim.allowBuild(player, Material.ARMOR_STAND) != null) {
            return "actionbar.claim_denied";
        }

        return null;
    }

    private EulerAngle getLimbAngle(ArmorStand stand, String toolId) {
        return switch (toolId) {
            case "pose_head" -> stand.getHeadPose();
            case "pose_body" -> stand.getBodyPose();
            case "pose_l_arm" -> stand.getLeftArmPose();
            case "pose_r_arm" -> stand.getRightArmPose();
            case "pose_l_leg" -> stand.getLeftLegPose();
            case "pose_r_leg" -> stand.getRightLegPose();
            default -> new EulerAngle(0,0,0);
        };
    }

    private void setLimbAngle(ArmorStand stand, String toolId, EulerAngle angle) {
        switch (toolId) {
            case "pose_head" -> stand.setHeadPose(angle);
            case "pose_body" -> stand.setBodyPose(angle);
            case "pose_l_arm" -> stand.setLeftArmPose(angle);
            case "pose_r_arm" -> stand.setRightArmPose(angle);
            case "pose_l_leg" -> stand.setLeftLegPose(angle);
            case "pose_r_leg" -> stand.setRightLegPose(angle);
        }
    }

    private List<Material> getValidTamingItems(EntityType type) {
        return switch (type) {
            case WOLF -> List.of(Material.BONE);
            case CAT, OCELOT -> List.of(Material.COD, Material.SALMON);
            case PARROT -> List.of(Material.WHEAT_SEEDS, Material.MELON_SEEDS, Material.PUMPKIN_SEEDS, Material.BEETROOT_SEEDS, Material.TORCHFLOWER_SEEDS, Material.PITCHER_POD);
            case HORSE, DONKEY, MULE, ZOMBIE_HORSE, SKELETON_HORSE -> List.of(Material.GOLDEN_APPLE, Material.ENCHANTED_GOLDEN_APPLE, Material.GOLDEN_CARROT, Material.APPLE, Material.WHEAT, Material.SUGAR, Material.HAY_BLOCK);
            case LLAMA, TRADER_LLAMA -> List.of(Material.WHEAT, Material.HAY_BLOCK);
            case CAMEL -> List.of(Material.CACTUS);
            default -> List.of();
        };
    }

    private static class PoseClipboard {
        EulerAngle head, body, lArm, rArm, lLeg, rLeg;
        double scale = 1.0;
        boolean hasArms, hasBasePlate, isSmall, isVisible, isGlowing, isInvulnerable, customNameVisible;
        Component customName;

        PoseClipboard(ArmorStand stand) {
            this.head = stand.getHeadPose();
            this.body = stand.getBodyPose();
            this.lArm = stand.getLeftArmPose();
            this.rArm = stand.getRightArmPose();
            this.lLeg = stand.getLeftLegPose();
            this.rLeg = stand.getRightLegPose();

            this.hasArms = stand.hasArms();
            this.hasBasePlate = stand.hasBasePlate();
            this.isSmall = stand.isSmall();
            this.isVisible = stand.isVisible();
            this.isGlowing = stand.isGlowing();
            this.isInvulnerable = stand.isInvulnerable();

            this.customNameVisible = stand.isCustomNameVisible();
            this.customName = stand.customName();

            if (stand.getAttribute(Attribute.SCALE) != null) {
                this.scale = stand.getAttribute(Attribute.SCALE).getBaseValue();
            }
        }

        void applyTo(ArmorStand stand) {
            stand.setHeadPose(head);
            stand.setBodyPose(body);
            stand.setLeftArmPose(lArm);
            stand.setRightArmPose(rArm);
            stand.setLeftLegPose(lLeg);
            stand.setRightLegPose(rLeg);

            stand.setArms(hasArms);
            stand.setBasePlate(hasBasePlate);
            stand.setSmall(isSmall);
            stand.setVisible(isVisible);
            stand.setGlowing(isGlowing);
            stand.setInvulnerable(isInvulnerable);

            stand.setCustomNameVisible(customNameVisible);
            stand.customName(customName);

            if (stand.getAttribute(Attribute.SCALE) != null) {
                stand.getAttribute(Attribute.SCALE).setBaseValue(scale);
            }
        }
    }
}