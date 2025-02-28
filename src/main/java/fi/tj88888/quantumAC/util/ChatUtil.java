package fi.tj88888.quantumAC.util;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.awt.Color;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enhanced utility class for chat-related functions
 */
public class ChatUtil {

    // Regex pattern for hex color codes like &#RRGGBB
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    // Common prefixes used throughout the plugin
    private static final String PREFIX = "&7[&bQuantum&7] ";
    private static final String INFO_PREFIX = PREFIX + "&b";
    private static final String SUCCESS_PREFIX = PREFIX + "&a";
    private static final String WARNING_PREFIX = PREFIX + "&e";
    private static final String ERROR_PREFIX = PREFIX + "&c";
    private static final String DEBUG_PREFIX = PREFIX + "&d";

    /**
     * Colorizes a string using Bukkit's color codes
     * Also supports hex colors with &#RRGGBB format
     *
     * @param message Message to colorize
     * @return Colorized message
     */
    public static String colorize(String message) {
        if (message == null) return "";

        // Process hex colors first (if supported by server version)
        try {
            Matcher matcher = HEX_PATTERN.matcher(message);
            StringBuffer buffer = new StringBuffer();

            while (matcher.find()) {
                String hex = matcher.group(1);
                matcher.appendReplacement(buffer, ChatColor.of("#" + hex).toString());
            }

            matcher.appendTail(buffer);
            message = buffer.toString();
        } catch (NoSuchMethodError e) {
            // Hex colors not supported in this version, just continue with regular colors
        }

        // Process standard color codes
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', message);
    }

    /**
     * Removes color codes from a string
     *
     * @param message Message to strip
     * @return Plain message without color codes
     */
    public static String stripColor(String message) {
        return org.bukkit.ChatColor.stripColor(message);
    }

    /**
     * Creates a gradient color text
     *
     * @param message Message to apply gradient to
     * @param from Starting color
     * @param to Ending color
     * @return Message with color gradient
     */
    public static String gradient(String message, Color from, Color to) {
        if (message == null || message.isEmpty()) return "";

        StringBuilder builder = new StringBuilder();

        // Remove existing color codes for clean processing
        message = stripColor(message);

        // Calculate the color step based on message length
        int length = message.length();

        for (int i = 0; i < length; i++) {
            float ratio = (float) i / (float) (length - 1);

            // Interpolate between the two colors
            int red = (int) (from.getRed() * (1 - ratio) + to.getRed() * ratio);
            int green = (int) (from.getGreen() * (1 - ratio) + to.getGreen() * ratio);
            int blue = (int) (from.getBlue() * (1 - ratio) + to.getBlue() * ratio);

            // Create hex color code
            String hex = String.format("#%02x%02x%02x", red, green, blue);

            try {
                builder.append(ChatColor.of(hex)).append(message.charAt(i));
            } catch (NoSuchMethodError e) {
                // Fallback for older versions without hex support
                builder.append(message.charAt(i));
            }
        }

        return builder.toString();
    }

    /**
     * Creates a progress bar visualization
     *
     * @param progress Value between 0.0 and 1.0
     * @param length The length of the progress bar
     * @param filledColor Color for filled portion
     * @param emptyColor Color for empty portion
     * @param barChar Character to use for the bar
     * @return Formatted progress bar
     */
    public static String createProgressBar(double progress, int length, String filledColor, String emptyColor, char barChar) {
        int filledBars = (int) Math.round(progress * length);
        if (filledBars > length) filledBars = length;

        StringBuilder builder = new StringBuilder();
        builder.append(colorize(filledColor));

        for (int i = 0; i < filledBars; i++) {
            builder.append(barChar);
        }

        builder.append(colorize(emptyColor));

        for (int i = filledBars; i < length; i++) {
            builder.append(barChar);
        }

        return builder.toString();
    }

    /**
     * Creates a violation level bar
     *
     * @param vl Current violation level
     * @param maxVL Maximum violation level for full bar
     * @return Formatted violation level bar
     */
    public static String createViolationBar(double vl, double maxVL) {
        double progress = Math.min(1.0, vl / maxVL);
        String color;

        if (progress < 0.33) {
            color = "&a"; // Green for low violations
        } else if (progress < 0.66) {
            color = "&e"; // Yellow for medium violations
        } else {
            color = "&c"; // Red for high violations
        }

        return color + "VL: " + String.format("%.1f", vl) + " " +
                createProgressBar(progress, 10, color, "&7", '|');
    }

    /**
     * Centers a message in chat
     *
     * @param message Message to center
     * @return Centered message
     */
    public static String centerMessage(String message) {
        message = colorize(message);
        int messagePxSize = 0;
        boolean previousCode = false;
        boolean isBold = false;

        for (char c : message.toCharArray()) {
            if (c == '§') {
                previousCode = true;
            } else if (previousCode) {
                previousCode = false;
                isBold = c == 'l' || c == 'L';
            } else {
                DefaultFontInfo dFI = DefaultFontInfo.getDefaultFontInfo(c);
                messagePxSize += isBold ? dFI.getBoldLength() : dFI.getLength();
                messagePxSize++;
            }
        }

        int halvedMessageSize = messagePxSize / 2;
        int toCompensate = 154 - halvedMessageSize;
        int spaceLength = DefaultFontInfo.SPACE.getLength() + 1;
        int compensated = 0;
        StringBuilder sb = new StringBuilder();

        while (compensated < toCompensate) {
            sb.append(" ");
            compensated += spaceLength;
        }

        return sb.toString() + message;
    }

    /**
     * Creates a header with a centered title and separator lines
     *
     * @param title The title to display in the header
     * @return Formatted header
     */
    public static String createHeader(String title) {
        String line = "&7" + String.join("", Collections.nCopies(40, "-"));
        return colorize(line + "\n" + centerMessage("&b" + title) + "\n" + line);
    }

    /**
     * Creates a footer with a separator line
     *
     * @return Formatted footer
     */
    public static String createFooter() {
        return colorize("&7" + String.join("", Collections.nCopies(40, "-")));
    }

    /**
     * Sends a standard info message with prefix
     *
     * @param sender Command sender
     * @param message Message to send
     */
    public static void sendInfo(CommandSender sender, String message) {
        sender.sendMessage(colorize(INFO_PREFIX + message));
    }

    /**
     * Sends a success message with prefix
     *
     * @param sender Command sender
     * @param message Message to send
     */
    public static void sendSuccess(CommandSender sender, String message) {
        sender.sendMessage(colorize(SUCCESS_PREFIX + message));
    }

    /**
     * Sends a warning message with prefix
     *
     * @param sender Command sender
     * @param message Message to send
     */
    public static void sendWarning(CommandSender sender, String message) {
        sender.sendMessage(colorize(WARNING_PREFIX + message));
    }

    /**
     * Sends an error message with prefix
     *
     * @param sender Command sender
     * @param message Message to send
     */
    public static void sendError(CommandSender sender, String message) {
        sender.sendMessage(colorize(ERROR_PREFIX + message));
    }

    /**
     * Sends a debug message with prefix
     *
     * @param sender Command sender
     * @param message Message to send
     */
    public static void sendDebug(CommandSender sender, String message) {
        sender.sendMessage(colorize(DEBUG_PREFIX + message));
    }

    /**
     * Creates a clickable message that executes a command when clicked
     *
     * @param sender Command sender (must be a Player)
     * @param message Message text
     * @param command Command to execute without the slash
     * @param hoverText Text to show when hovering (can be null)
     */
    public static void sendClickableCommand(CommandSender sender, String message, String command, String hoverText) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(colorize(message));
            return;
        }

        TextComponent component = new TextComponent(colorize(message));
        component.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + command));

        if (hoverText != null) {
            component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new ComponentBuilder(colorize(hoverText)).create()));
        }

        ((Player) sender).spigot().sendMessage(component);
    }

    /**
     * Creates a clickable message that suggests a command when clicked
     *
     * @param sender Command sender (must be a Player)
     * @param message Message text
     * @param command Command to suggest without the slash
     * @param hoverText Text to show when hovering (can be null)
     */
    public static void sendSuggestCommand(CommandSender sender, String message, String command, String hoverText) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(colorize(message));
            return;
        }

        TextComponent component = new TextComponent(colorize(message));
        component.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/" + command));

        if (hoverText != null) {
            component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new ComponentBuilder(colorize(hoverText)).create()));
        }

        ((Player) sender).spigot().sendMessage(component);
    }

    /**
     * Creates a paginated list of items
     *
     * @param title Header title
     * @param items List of items to display
     * @param page Current page (1-based)
     * @param itemsPerPage Number of items per page
     * @return Formatted paginated list
     */
    public static List<String> createPaginatedList(String title, List<String> items, int page, int itemsPerPage) {
        List<String> result = new ArrayList<>();

        int totalPages = (int) Math.ceil((double) items.size() / itemsPerPage);
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;

        result.add(createHeader(title + " &7(Page &b" + page + "&7/&b" + totalPages + "&7)"));

        if (items.isEmpty()) {
            result.add(colorize("&7No items to display."));
        } else {
            int start = (page - 1) * itemsPerPage;
            int end = Math.min(start + itemsPerPage, items.size());

            for (int i = start; i < end; i++) {
                result.add(colorize(items.get(i)));
            }
        }

        if (totalPages > 1) {
            StringBuilder navigation = new StringBuilder("&7");
            if (page > 1) {
                navigation.append("&a◀ Previous ");
            } else {
                navigation.append("&8◀ Previous ");
            }

            navigation.append("&7|");

            if (page < totalPages) {
                navigation.append(" &aNext ▶");
            } else {
                navigation.append(" &8Next ▶");
            }

            result.add(centerMessage(navigation.toString()));
        }

        result.add(createFooter());
        return result;
    }

    /**
     * Enum for Minecraft's default font character widths
     */
    public enum DefaultFontInfo {
        A('A', 5),
        a('a', 5),
        B('B', 5),
        b('b', 5),
        C('C', 5),
        c('c', 5),
        D('D', 5),
        d('d', 5),
        E('E', 5),
        e('e', 5),
        F('F', 5),
        f('f', 4),
        G('G', 5),
        g('g', 5),
        H('H', 5),
        h('h', 5),
        I('I', 3),
        i('i', 1),
        J('J', 5),
        j('j', 5),
        K('K', 5),
        k('k', 4),
        L('L', 5),
        l('l', 1),
        M('M', 5),
        m('m', 5),
        N('N', 5),
        n('n', 5),
        O('O', 5),
        o('o', 5),
        P('P', 5),
        p('p', 5),
        Q('Q', 5),
        q('q', 5),
        R('R', 5),
        r('r', 5),
        S('S', 5),
        s('s', 5),
        T('T', 5),
        t('t', 4),
        U('U', 5),
        u('u', 5),
        V('V', 5),
        v('v', 5),
        W('W', 5),
        w('w', 5),
        X('X', 5),
        x('x', 5),
        Y('Y', 5),
        y('y', 5),
        Z('Z', 5),
        z('z', 5),
        NUM_1('1', 5),
        NUM_2('2', 5),
        NUM_3('3', 5),
        NUM_4('4', 5),
        NUM_5('5', 5),
        NUM_6('6', 5),
        NUM_7('7', 5),
        NUM_8('8', 5),
        NUM_9('9', 5),
        NUM_0('0', 5),
        EXCLAMATION_POINT('!', 1),
        AT_SYMBOL('@', 6),
        NUM_SIGN('#', 5),
        DOLLAR_SIGN('$', 5),
        PERCENT('%', 5),
        UP_ARROW('^', 5),
        AMPERSAND('&', 5),
        ASTERISK('*', 5),
        LEFT_PARENTHESIS('(', 4),
        RIGHT_PARENTHESIS(')', 4),
        MINUS('-', 5),
        UNDERSCORE('_', 5),
        PLUS_SIGN('+', 5),
        EQUALS_SIGN('=', 5),
        LEFT_CURLY_BRACE('{', 4),
        RIGHT_CURLY_BRACE('}', 4),
        LEFT_BRACKET('[', 3),
        RIGHT_BRACKET(']', 3),
        COLON(':', 1),
        SEMI_COLON(';', 1),
        DOUBLE_QUOTE('"', 3),
        SINGLE_QUOTE('\'', 1),
        LEFT_ARROW('<', 4),
        RIGHT_ARROW('>', 4),
        QUESTION_MARK('?', 5),
        SLASH('/', 5),
        BACK_SLASH('\\', 5),
        LINE('|', 1),
        TILDE('~', 5),
        TICK('`', 2),
        PERIOD('.', 1),
        COMMA(',', 1),
        SPACE(' ', 3),
        DEFAULT('a', 4);

        private final char character;
        private final int length;

        DefaultFontInfo(char character, int length) {
            this.character = character;
            this.length = length;
        }

        public char getCharacter() {
            return character;
        }

        public int getLength() {
            return length;
        }

        public int getBoldLength() {
            return this == DefaultFontInfo.SPACE ? getLength() : getLength() + 1;
        }

        public static DefaultFontInfo getDefaultFontInfo(char c) {
            for (DefaultFontInfo dFI : DefaultFontInfo.values()) {
                if (dFI.getCharacter() == c) {
                    return dFI;
                }
            }
            return DefaultFontInfo.DEFAULT;
        }
    }
}