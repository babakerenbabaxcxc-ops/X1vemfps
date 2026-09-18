package x1vemfps.modid.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.Method;
import java.util.Locale;

/** Compact FiveM-inspired performance HUD for X1vemFPS. */
public final class X1vemFPSClient implements ClientModInitializer {
    private static final int X = 8;
    private static final int Y = 8;
    private static final int HEIGHT = 20;
    private static final int FPS_WIDTH = 72;
    private static final int CPU_WIDTH = 78;
    private static final int GPU_WIDTH = 78;

    private static double cpuLoad = Double.NaN;
    private static long lastSample;
    private static long lastCpu;
    private static long lastWall;

    @Override
    public void onInitializeClient() {
        HudRenderCallback.EVENT.register((graphics, deltaTracker) -> render(graphics));
    }

    private static void render(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        int x = X;
        drawPanel(g, x, FPS_WIDTH, "FPS", Integer.toString(mc.getFps()));
        x += FPS_WIDTH;
        drawPanel(g, x, CPU_WIDTH, "CPU", getCpu());
        x += CPU_WIDTH;
        drawPanel(g, x, GPU_WIDTH, "GPU", getGpu());
    }

    private static void drawPanel(GuiGraphics g, int x, int width, String label, String value) {
        // Layered 1px borders and translucent fill reproduce the thin HUD-box look.
        int fill = 0xC910141A;
        int border = 0xC777818D;
        int inner = 0x5F2A313A;
        int labelColor = 0xFF9EA8B3;
        int valueColor = 0xFFE8EDF2;

        g.fill(x, Y, x + width, Y + HEIGHT, fill);
        g.fill(x + 1, Y + 1, x + width - 1, Y + HEIGHT - 1, inner);
        g.fill(x, Y, x + width, Y + 1, border);
        g.fill(x, Y + HEIGHT - 1, x + width, Y + HEIGHT, border);
        g.fill(x, Y, x + 1, Y + HEIGHT, border);
        g.fill(x + width - 1, Y, x + width, Y + HEIGHT, border);

        // Tiny divider at the label/value boundary.
        int dividerX = x + 31;
        g.fill(dividerX, Y + 4, dividerX + 1, Y + HEIGHT - 4, 0x6677818D);

        var font = Minecraft.getInstance().font;
        g.drawString(font, Component.literal(label), x + 6, Y + 6, labelColor, false);
        int valueWidth = font.width(value);
        g.drawString(font, Component.literal(value), x + width - 6 - valueWidth, Y + 6, valueColor, false);
    }

    private static String getCpu() {
        long now = System.nanoTime();
        if (lastWall == 0) {
            lastWall = now;
            lastCpu = getProcessCpuTime();
            lastSample = now;
            return "--%";
        }
        if (now - lastSample >= 500_000_000L) {
            long cpu = getProcessCpuTime();
            long wallDelta = now - lastWall;
            long cpuDelta = cpu - lastCpu;
            int cores = Math.max(1, Runtime.getRuntime().availableProcessors());
            if (wallDelta > 0 && cpuDelta >= 0) {
                cpuLoad = Math.max(0, Math.min(100, cpuDelta * 100.0 / wallDelta / cores));
            }
            lastCpu = cpu;
            lastWall = now;
            lastSample = now;
        }
        return Double.isNaN(cpuLoad) ? "--%" : String.format(Locale.ROOT, "%.0f%%", cpuLoad);
    }

    private static long getProcessCpuTime() {
        try {
            OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
            Method m = os.getClass().getMethod("getProcessCpuTime");
            m.setAccessible(true);
            Object result = m.invoke(os);
            return result instanceof Long l ? l : 0L;
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    private static String getGpu() {
        // Java/Fabric does not expose Android GPU utilization universally.
        // Show a truthful availability marker instead of inventing a GPU percentage.
        return "N/A";
    }
}
