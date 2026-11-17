
package com.krypton.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class KryptonScreen extends Screen {
    private int panelX, panelY, panelW, panelH;

    public KryptonScreen() {
        super(Component.literal("Krypton Client"));
    }

    @Override
    protected void init() {
        panelW = Math.min(520, width - 30);
        panelH = Math.min(340, height - 30);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        renderBackground(g, mouseX, mouseY, delta);

        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xEE10131D);
        g.fill(panelX, panelY, panelX + panelW, panelY + 34, 0xFF171E30);

        g.drawString(font, "KRYPTON CLIENT", panelX + 14, panelY + 12, 0xFF75D7FF, false);
        g.drawString(font, "1.21.11 | RIGHT SHIFT: close | K: rescan",
                panelX + 14, panelY + 44, 0xFF9AA8BD, false);

        int y = panelY + 70;

        for (Module.Category category : Module.Category.values()) {
            g.drawString(font, category.name(), panelX + 14, y, 0xFF75D7FF, false);
            y += 17;

            for (Module module : KryptonClient.MODULES) {
                if (module.category() != category) continue;

                int color = module.enabled() ? 0xFF39D98A : 0xFF7B8496;
                g.fill(panelX + 12, y - 3, panelX + panelW - 12, y + 14, 0xFF1A2030);

                g.drawString(font,
                        (module.enabled() ? "[ON]  " : "[OFF] ") + module.name(),
                        panelX + 20, y, color, false);

                g.drawString(font, module.description(),
                        panelX + 190, y, 0xFFB0B8C8, false);

                y += 20;
            }
            y += 8;
        }

        int reportY = panelY + panelH - 66;
        g.drawString(font, "LOADED CHUNK SCAN",
                panelX + 14, reportY, 0xFFFFC66D, false);

        String summary = KryptonClient.REPORTS.stream()
                .limit(4)
                .map(r -> "[" + r.chunkX() + "," + r.chunkZ()
                        + "] storage:" + r.storageCount()
                        + (r.suspicious() ? " !" : ""))
                .reduce((a, b) -> a + "   " + b)
                .orElse("Press K to scan nearby loaded chunks");

        g.drawString(font, summary,
                panelX + 14, reportY + 16, 0xFFE2E7F0, false);

        g.drawString(font, "Click a module row to toggle it. Esc closes this menu.",
                panelX + 14, panelY + panelH - 18, 0xFF8995A9, false);

        super.render(g, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();

        if (event.button() == 0) {
            int y = panelY + 70;

            for (Module.Category category : Module.Category.values()) {
                y += 17;

                for (Module module : KryptonClient.MODULES) {
                    if (module.category() != category) continue;

                    if (mouseY >= y - 3 && mouseY <= y + 14
                            && mouseX >= panelX + 12
                            && mouseX <= panelX + panelW - 12) {
                        module.toggle();
                    }
                    y += 20;
                }
                y += 8;
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
