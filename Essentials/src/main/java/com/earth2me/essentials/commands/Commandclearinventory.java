package com.earth2me.essentials.commands;

import com.earth2me.essentials.CommandSource;
import com.earth2me.essentials.User;
import com.earth2me.essentials.craftbukkit.Inventories;
import com.earth2me.essentials.utils.NumberUtil;
import com.earth2me.essentials.utils.StringUtil;
import com.earth2me.essentials.utils.VersionUtil;
import net.ess3.api.TranslatableException;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class Commandclearinventory extends EssentialsCommand {
    private static final int EXTENDED_CAP = 8;

    public Commandclearinventory() {
        super("clearinventory");
    }

    @Override
    public void run(final Server server, final User user, final String commandLabel, final String[] args) throws Exception {
        parseCommand(server, user.getSource(), commandLabel, args, user.isAuthorized("essentials.clearinventory.others"),
            user.isAuthorized("essentials.clearinventory.all") || user.isAuthorized("essentials.clearinventory.multiple"));
    }

    @Override
    protected void run(final Server server, final CommandSource sender, final String commandLabel, final String[] args) throws Exception {
        parseCommand(server, sender, commandLabel, args, true, true);
    }

    private void parseCommand(final Server server, final CommandSource sender, final String commandLabel, final String[] args, final boolean allowOthers, final boolean allowAll)
        throws Exception {
        Collection<Player> players = new ArrayList<>();
        final User senderUser = ess.getUser(sender.getPlayer());
        String previousClearCommand = "";

        int offset = 0;

        if (sender.isPlayer()) {
            players.add(sender.getPlayer());
            // Clear previous command execution before potential errors to reset confirmation.
            previousClearCommand = senderUser.getConfirmingClearCommand();
            senderUser.setConfirmingClearCommand(null);
        }

        if (allowAll && args.length > 0 && args[0].contentEquals("*")) {
            sender.sendTl("inventoryClearingFromAll");
            offset = 1;
            players = ess.getOnlinePlayers();
        } else if (allowOthers && args.length > 0 && args[0].trim().length() > 2) {
            offset = 1;
            players = server.matchPlayer(args[0].trim());
        }

        if (players.size() < 1) {
            throw new PlayerNotFoundException();
        }

        // Confirm
        final String formattedCommand = formatCommand(commandLabel, args);
        if (senderUser != null && senderUser.isPromptingClearConfirm()) {
            if (!formattedCommand.equals(previousClearCommand)) {
                senderUser.setConfirmingClearCommand(formattedCommand);
                senderUser.sendTl("confirmClear", formattedCommand);
                return;
            }
        }

        for (final Player player : players) {
            clearHandler(sender, player, args, offset, players.size() < EXTENDED_CAP);
        }
    }

    protected void clearHandler(final CommandSource sender, final Player player, final String[] args, final int offset, final boolean showExtended) throws TranslatableException {
        ClearHandlerType type = ClearHandlerType.ALL_EXCEPT_ARMOR;
        final Set<Item> items = new HashSet<>();
        int amount = -1;

        if (args.length > (offset + 1) && NumberUtil.isInt(args[offset + 1])) {
            amount = Integer.parseInt(args[offset + 1]);
        }
        if (args.length > offset) {
            if (args[offset].equalsIgnoreCase("**")) {
                type = ClearHandlerType.ALL_INCLUDING_ARMOR;
            } else if (!args[offset].equalsIgnoreCase("*")) {
                final String[] split = args[offset].split(",");
                for (final String item : split) {
                    final String[] itemParts = item.split(":");
                    short data;
                    try {
                        data = Short.parseShort(itemParts[1]);
                    } catch (final Exception e) {
                        data = 0;
                    }
                    try {
                        items.add(new Item(ess.getItemDb().get(itemParts[0]).getType(), data));
                    } catch (final Exception ignored) {
                    }
                }
                type = ClearHandlerType.SPECIFIC_ITEM;
            }
        }

        if (type != ClearHandlerType.SPECIFIC_ITEM) {
            final boolean armor = type == ClearHandlerType.ALL_INCLUDING_ARMOR;
            if (showExtended) {
                sender.sendTl(armor ? "inventoryClearingAllArmor" : "inventoryClearingAllItems", player.getDisplayName());
            }
            Inventories.removeItems(player, item -> true, armor);
        } else {
            for (final Item item : items) {
                final ItemStack stack = new ItemStack(item.getMaterial());
                if (VersionUtil.PRE_FLATTENING) {
                    //noinspection deprecation
                    stack.setDurability(item.getData());
                }

                // can't remove a negative amount of items. (it adds them)
                if (amount < -1) {
                    throw new TranslatableException("cannotRemoveNegativeItems");
                }

                // amount -1 means all items will be cleared
                if (amount == -1) {
                    final int removedAmount = Inventories.removeItemSimilar(player, stack, true);
                    if (removedAmount > 0 || showExtended) {
                        sender.sendTl("inventoryClearingStack", removedAmount, stack.getType().toString().toLowerCase(Locale.ENGLISH), player.getDisplayName());
                    }
                } else {
                    if (Inventories.removeItemAmount(player, stack, amount)) {
                        sender.sendTl("inventoryClearingStack", amount, stack.getType().toString().toLowerCase(Locale.ENGLISH), player.getDisplayName());
                    } else {
                        if (showExtended) {
                            sender.sendTl("inventoryClearFail", player.getDisplayName(), amount, stack.getType().toString().toLowerCase(Locale.ENGLISH));
                        }
                    }
                }
            }
        }
    }

    @Override
    protected List<String> getTabCompleteOptions(final Server server, final User user, final String commandLabel, final String[] args) {
        if (user.isAuthorized("essentials.clearinventory.others")) {
            if (args.length == 1) {
                final List<String> options = getPlayers(server, user);
                if (user.isAuthorized("essentials.clearinventory.all") || user.isAuthorized("essentials.clearinventory.multiple")) {
                    // Assume that nobody will have the 'all' permission without the 'others' permission
                    options.add("*");
                }
                return options;
            } else if (args.length == 2) {
                final List<String> items = new ArrayList<>(getItems());
                items.add("*");
                items.add("**");
                return items;
            } else {
                return Collections.emptyList();
            }
        } else {
            if (args.length == 1) {
                final List<String> items = new ArrayList<>(getItems());
                items.add("*");
                items.add("**");
                return items;
            } else {
                return Collections.emptyList();
            }
        }
    }

    @Override
    protected List<String> getTabCompleteOptions(final Server server, final CommandSource sender, final String commandLabel, final String[] args) {
        if (args.length == 1) {
            final List<String> options = getPlayers(server, sender);
            options.add("*");
            return options;
        } else if (args.length == 2) {
            final List<String> items = new ArrayList<>(getItems());
            items.add("*");
            items.add("**");
            return items;
        } else {
            return Collections.emptyList();
        }
    }

    private String formatCommand(final String commandLabel, final String[] args) {
        return "/" + commandLabel + " " + StringUtil.joinList(" ", (Object[]) args);
    }

    private enum ClearHandlerType {
        ALL_EXCEPT_ARMOR, ALL_INCLUDING_ARMOR, SPECIFIC_ITEM
    }

    private static class Item {
        private final Material material;
        private final short data;

        Item(final Material material, final short data) {
            this.material = material;
            this.data = data;
        }

        public Material getMaterial() {
            return material;
        }

        public short getData() {
            return data;
        }
    }
}
