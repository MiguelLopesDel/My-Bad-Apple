package dev.badapple.terminal;

import dev.badapple.render.Ansi;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.utils.NonBlockingReader;

import java.io.PrintWriter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Asks the terminal directly what it supports, by writing query escapes and reading the
 * replies with a short timeout. Uses the Primary Device Attributes reply as a sentinel since
 * virtually every terminal answers it. Anything unanswered simply leaves the env guess intact.
 *
 * <ul>
 *   <li>{@code CSI c} (DA1) — attribute {@code 4} indicates sixel support
 *   <li>{@code CSI 16 t} — reports cell size in pixels (for image-backend resolution)
 *   <li>kitty graphics query — a {@code ;OK} reply means the kitty protocol is available
 * </ul>
 */
public final class AnsiQuery {

    private static final Pattern CELL_SIZE = Pattern.compile("\\[6;(\\d+);(\\d+)t");
    private static final Pattern DA1 = Pattern.compile("\\[\\?([0-9;]+)c");

    private AnsiQuery() {
    }

    public static void refine(Terminal terminal, TerminalCapabilities caps) throws Exception {
        Attributes saved = terminal.enterRawMode();
        try {
            PrintWriter writer = terminal.writer();
            // Cell size, kitty graphics probe, then DA1 last as the sentinel.
            writer.print(Ansi.CSI + "16t");
            writer.print(Ansi.ESC + "_Gi=1,s=1,v=1,a=q,t=d,f=24;AAAA" + Ansi.ESC + "\\");
            writer.print(Ansi.CSI + "c");
            writer.flush();

            String response = readResponse(terminal.reader(), 350);
            parse(response, caps);
        } finally {
            terminal.setAttributes(saved);
        }
    }

    private static String readResponse(NonBlockingReader reader, int totalBudgetMs) throws Exception {
        StringBuilder sb = new StringBuilder();
        long deadline = System.currentTimeMillis() + totalBudgetMs;
        while (System.currentTimeMillis() < deadline) {
            int ch = reader.read(60);
            if (ch == NonBlockingReader.READ_EXPIRED) {
                if (sb.length() > 0) {
                    break; // a gap after some data: assume the terminal is done
                }
                continue;
            }
            if (ch < 0) {
                break;
            }
            sb.append((char) ch);
            // DA1 reply ends with 'c' and is sent last: a reliable stop signal.
            if (ch == 'c' && sb.indexOf("[?") >= 0) {
                break;
            }
        }
        return sb.toString();
    }

    private static void parse(String response, TerminalCapabilities caps) {
        Matcher cell = CELL_SIZE.matcher(response);
        if (cell.find()) {
            caps.cellHeightPx = Integer.parseInt(cell.group(1));
            caps.cellWidthPx = Integer.parseInt(cell.group(2));
        }
        Matcher da1 = DA1.matcher(response);
        if (da1.find()) {
            for (String attr : da1.group(1).split(";")) {
                if (attr.equals("4")) {
                    caps.sixel = true;
                }
            }
        }
        // kitty graphics reply: ESC _G ... ; OK ... ST
        if (response.contains("_G") && response.contains(";OK")) {
            caps.kitty = true;
        }
    }
}
