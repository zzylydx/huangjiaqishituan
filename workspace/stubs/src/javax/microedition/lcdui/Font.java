package javax.microedition.lcdui;

import java.util.Vector;
import host.js.JS;

/**
 * MIDP Font 桩。
 * 尺寸映射（贴近 2008 年 240x320 机型默认字体行高）：
 *   SMALL -> css 11px / 行高 14；MEDIUM -> css 13px / 行高 16；LARGE -> css 15px / 行高 18。
 * 浏览器以设备分辨率栅格化文本，3x 变换下文字天然高清。
 */
public class Font {
    public static final int FACE_SYSTEM = 0;
    public static final int FACE_MONOSPACE = 32;
    public static final int FACE_PROPORTIONAL = 64;

    public static final int STYLE_PLAIN = 0;
    public static final int STYLE_BOLD = 1;
    public static final int STYLE_ITALIC = 2;
    public static final int STYLE_UNDERLINED = 4;

    public static final int SIZE_SMALL = 8;
    public static final int SIZE_MEDIUM = 0;
    public static final int SIZE_LARGE = 16;

    private static final Vector CACHE = new Vector();

    final int face;
    final int style;
    final int size;
    final int cssPx;
    final int height;
    final String css;

    private Font(int face, int style, int size) {
        this.face = face;
        this.style = style;
        this.size = size;
        switch (size) {
            case SIZE_SMALL:
                this.cssPx = 11;
                this.height = 14;
                break;
            case SIZE_LARGE:
                this.cssPx = 15;
                this.height = 18;
                break;
            case SIZE_MEDIUM:
            default:
                this.cssPx = 13;
                this.height = 16;
                break;
        }
        StringBuilder sb = new StringBuilder();
        if ((style & STYLE_BOLD) != 0) {
            sb.append("bold ");
        }
        if ((style & STYLE_ITALIC) != 0) {
            sb.append("italic ");
        }
        sb.append(cssPx).append("px ");
        if (face == FACE_MONOSPACE) {
            sb.append("'Cutive Mono', 'Courier New', monospace");
        } else {
            sb.append("system-ui, -apple-system, 'PingFang SC', 'Microsoft YaHei', sans-serif");
        }
        this.css = sb.toString();
    }

    public static Font getFont(int face, int style, int size) {
        for (int i = 0; i < CACHE.size(); i++) {
            Font f = (Font) CACHE.elementAt(i);
            if (f.face == face && f.style == style && f.size == size) {
                return f;
            }
        }
        Font f = new Font(face, style, size);
        CACHE.addElement(f);
        return f;
    }

    public static Font getDefaultFont() {
        return getFont(FACE_SYSTEM, STYLE_PLAIN, SIZE_MEDIUM);
    }

    public int getHeight() {
        return height;
    }

    public int getCssPx() {
        return cssPx;
    }

    public String getCss() {
        return css;
    }

    public int stringWidth(String str) {
        if (str == null || str.length() == 0) {
            return 0;
        }
        return Graphics.measureText(this, str);
    }

    public int charWidth(char ch) {
        return stringWidth(String.valueOf(ch));
    }

    public int substringWidth(String str, int offset, int len) {
        return stringWidth(str.substring(offset, offset + len));
    }
}
