package dev.badapple.cli;

/**
 * Minimal CLI parser. Accepts {@code --flag value} and {@code --flag=value} forms.
 * Unknown flags are ignored so the surface can grow across phases without breaking.
 */
public final class Args {

    public String color = "mono";
    public String palette = null;
    public boolean noAudio = false;
    public String renderer = "auto";
    public boolean force = false;
    public boolean debug = false;
    public boolean help = false;

    public static Args parse(String[] argv) {
        Args a = new Args();
        for (int i = 0; i < argv.length; i++) {
            String arg = argv[i];
            String key = arg;
            String inline = null;
            if (arg.startsWith("--")) {
                int eq = arg.indexOf('=');
                if (eq > 0) {
                    key = arg.substring(0, eq);
                    inline = arg.substring(eq + 1);
                }
            }
            switch (key) {
                case "--color" -> {
                    a.color = inline != null ? inline : next(argv, i);
                    if (inline == null) {
                        i++;
                    }
                }
                case "--palette" -> {
                    a.palette = inline != null ? inline : next(argv, i);
                    if (inline == null) {
                        i++;
                    }
                }
                case "--renderer" -> {
                    a.renderer = inline != null ? inline : next(argv, i);
                    if (inline == null) {
                        i++;
                    }
                }
                case "--no-audio" -> a.noAudio = true;
                case "--force" -> a.force = true;
                case "--debug" -> a.debug = true;
                case "-h", "--help" -> a.help = true;
                default -> { /* ignore unknown */ }
            }
        }
        return a;
    }

    private static String next(String[] argv, int i) {
        return i + 1 < argv.length ? argv[i + 1] : null;
    }

    public static String help() {
        return """
                My Bad Apple — terminal player

                Usage: java -jar my-bad-apple.jar [options]

                Options:
                  --color <mode>     mono | hue | gradient | lut   (default: mono)
                  --palette <name>   fire | neon | aurora | ice    (gradient/lut)
                  --renderer <id>    auto | kitty | sixel | iterm | halfblock | ascii
                  --force            ignore terminal capability detection
                  --no-audio         play without sound
                  --debug            print detected capabilities and exit
                  -h, --help         show this help

                Controls:
                  space              pause / resume
                  left / right       seek -5s / +5s
                  up / down, +/-     speed up / slow down
                  ctrl-h             toggle the FPS counter
                  q, Esc             quit
                """;
    }

    private Args() {
    }
}
