package javax.microedition.lcdui;

import host.Host;
import host.js.JS;

import java.util.HashMap;

/**
 * MIDP Graphics 桩：把 lcdui 语义翻译到 Canvas2D。
 * - 逻辑坐标系 240x320，ctx.setTransform(3,0,0,3,tx*3,ty*3) 后全部按逻辑坐标绘制；
 *   iPhone 15 Pro dpr=3 时恰为设备像素 1:1，imageSmoothingEnabled=false 保证像素完美。
 * - clip 语义按 MIDP：setClip/clipRect 在"当前平移坐标系"中解释，getClip 返回当前坐标系值。
 * - drawRect 用 4 条 1px fillRect 实现整数像素边框（strokeRect 会产生半像素模糊）。
 * - 文本由浏览器按设备分辨率栅格化，天然高清。
 */
public class Graphics {
    public static final int HCENTER = 1;
    public static final int VCENTER = 3;
    public static final int LEFT = 4;
    public static final int RIGHT = 8;
    public static final int TOP = 16;
    public static final int BOTTOM = 32;
    public static final int BASELINE = 64;

    private final JS.JSCtx ctx;
    private Font font = Font.getDefaultFont();
    private int rgb = 0x000000;
    private String colorCss = "#000000";
    private int tx, ty;
    private int clipAbsX, clipAbsY, clipAbsW, clipAbsH;
    private static final HashMap MEASURE_CACHE = new HashMap();

    public Graphics(JS.JSCtx ctx) {
        this.ctx = ctx;
        reset();
    }

    void reset() {
        tx = 0;
        ty = 0;
        clipAbsX = 0;
        clipAbsY = 0;
        clipAbsW = Host.LOGICAL_W;
        clipAbsH = Host.LOGICAL_H;
    }

    // ---------- 状态 ----------

    public void setColor(int rgb) {
        if (rgb != this.rgb) {
            this.rgb = rgb & 0xFFFFFF;
            this.colorCss = null; // 惰性格式化
        }
    }

    public void setColor(int r, int g, int b) {
        setColor((r << 16) | (g << 8) | b);
    }

    public void setFont(Font font) {
        this.font = font == null ? Font.getDefaultFont() : font;
    }

    public Font getFont() {
        return font;
    }

    public void translate(int x, int y) {
        this.tx += x;
        this.ty += y;
    }

    public void setClip(int x, int y, int width, int height) {
        clipAbsX = x + tx;
        clipAbsY = y + ty;
        clipAbsW = width;
        clipAbsH = height;
        clampClip();
    }

    public void clipRect(int x, int y, int width, int height) {
        int ax = x + tx;
        int ay = y + ty;
        int nx1 = Math.max(clipAbsX, ax);
        int ny1 = Math.max(clipAbsY, ay);
        int nx2 = Math.min(clipAbsX + clipAbsW, ax + width);
        int ny2 = Math.min(clipAbsY + clipAbsH, ay + height);
        clipAbsX = nx1;
        clipAbsY = ny1;
        clipAbsW = Math.max(0, nx2 - nx1);
        clipAbsH = Math.max(0, ny2 - ny1);
    }

    public int getClipX() {
        return clipAbsX - tx;
    }

    public int getClipY() {
        return clipAbsY - ty;
    }

    public int getClipWidth() {
        return clipAbsW;
    }

    public int getClipHeight() {
        return clipAbsH;
    }

    private void clampClip() {
        int x1 = Math.max(0, clipAbsX);
        int y1 = Math.max(0, clipAbsY);
        int x2 = Math.min(Host.LOGICAL_W, clipAbsX + clipAbsW);
        int y2 = Math.min(Host.LOGICAL_H, clipAbsY + clipAbsH);
        clipAbsX = x1;
        clipAbsY = y1;
        clipAbsW = Math.max(0, x2 - x1);
        clipAbsH = Math.max(0, y2 - y1);
    }

    private boolean fullClip() {
        return clipAbsX <= 0 && clipAbsY <= 0 && clipAbsW >= Host.LOGICAL_W && clipAbsH >= Host.LOGICAL_H;
    }

    // ---------- 绘制原语 ----------

    public void fillRect(int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        begin(true);
        ctx.fillRect(x, y, width, height);
        end();
    }

    public void drawRect(int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        begin(true);
        ctx.fillRect(x, y, width, 1);
        ctx.fillRect(x, y + height - 1, width, 1);
        ctx.fillRect(x, y, 1, height);
        ctx.fillRect(x + width - 1, y, 1, height);
        end();
    }

    public void drawLine(int x1, int y1, int x2, int y2) {
        if (x1 == x2) {
            begin(true);
            ctx.fillRect(x1, Math.min(y1, y2), 1, Math.abs(y2 - y1) + 1);
            end();
        } else if (y1 == y2) {
            begin(true);
            ctx.fillRect(Math.min(x1, x2), y1, Math.abs(x2 - x1) + 1, 1);
            end();
        } else {
            begin(false);
            ctx.setStrokeStyle(color());
            ctx.setLineWidth(1.0);
            ctx.beginPath();
            ctx.moveTo(x1 + 0.5, y1 + 0.5);
            ctx.lineTo(x2 + 0.5, y2 + 0.5);
            ctx.stroke();
            end();
        }
    }

    public void fillArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
        if (width <= 0 || height <= 0 || arcAngle == 0) {
            return;
        }
        begin(true);
        double rx = width / 2.0;
        double ry = height / 2.0;
        double cx = x + rx;
        double cy = y + ry;
        double a0 = -startAngle * 0.017453292519943295;
        double a1 = -(startAngle + arcAngle) * 0.017453292519943295;
        if (Math.abs(arcAngle) >= 360) {
            ctx.arc(cx, cy, Math.max(rx, ry), 0, Math.PI * 2);
        } else {
            ctx.ellipse(cx, cy, rx, ry, 0, a0, a1);
        }
        ctx.fill();
        end();
    }

    public void drawImage(Image img, int x, int y, int anchor) {
        if (img == null || img.element == null) {
            return;
        }
        int w = img.width;
        int h = img.height;
        int dx = x;
        int dy = y;
        if ((anchor & RIGHT) != 0) {
            dx = x - w;
        } else if ((anchor & HCENTER) != 0) {
            dx = x - (w >> 1);
        }
        if ((anchor & BOTTOM) != 0) {
            dy = y - h;
        } else if ((anchor & VCENTER) != 0) {
            dy = y - (h >> 1);
        }
        begin(false);
        ctx.drawImage(img.element, dx, dy);
        end();
    }

    public void drawString(String str, int x, int y, int anchor) {
        if (str == null || str.length() == 0) {
            return;
        }
        int w = font.stringWidth(str);
        int dx = x;
        if ((anchor & RIGHT) != 0) {
            dx = x - w;
        } else if ((anchor & HCENTER) != 0) {
            dx = x - (w >> 1);
        }
        int dy = y;
        if ((anchor & BOTTOM) != 0) {
            dy = y - Math.round(font.getCssPx() * 0.8f);
        } else if ((anchor & BASELINE) == 0) { // 默认 TOP
            dy = y + Math.round(font.getCssPx() * 0.82f);
        }
        begin(false);
        ctx.setFillStyle(color());
        ctx.setFont(font.getCss());
        ctx.setTextBaseline("alphabetic");
        ctx.fillText(str, dx, dy);
        end();
    }

    // ---------- 状态应用 ----------

    private String color() {
        if (colorCss == null) {
            StringBuilder sb = new StringBuilder(7);
            sb.append('#');
            String hex = Integer.toHexString(rgb & 0xFFFFFF);
            for (int i = hex.length(); i < 6; i++) {
                sb.append('0');
            }
            sb.append(hex);
            colorCss = sb.toString();
        }
        return colorCss;
    }

    private void begin(boolean fill) {
        ctx.save();
        ctx.setTransform(Host.SCALE_X, 0, 0, Host.SCALE_Y, tx * Host.SCALE_X, ty * Host.SCALE_Y);
        if (!fullClip()) {
            ctx.beginPath();
            ctx.rect(clipAbsX - tx, clipAbsY - ty, clipAbsW, clipAbsH);
            ctx.clip();
        }
        if (fill) {
            ctx.setFillStyle(color());
        }
    }

    private void end() {
        ctx.restore();
    }

    // ---------- 文本测量 ----------

    static int measureText(Font font, String str) {
        String key = font.getCss() + "|" + str;
        Object cached = MEASURE_CACHE.get(key);
        if (cached != null) {
            return ((Integer) cached).intValue();
        }
        JS.JSCtx c = Host.textCtx();
        c.setFont(font.getCss());
        double width = c.measureText(str).getWidth();
        int result = (int) Math.ceil(width);
        MEASURE_CACHE.put(key, new Integer(result));
        return result;
    }
}
