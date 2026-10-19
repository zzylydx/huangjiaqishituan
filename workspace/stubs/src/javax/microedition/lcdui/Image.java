package javax.microedition.lcdui;

import host.Host;
import host.js.JS;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * MIDP Image 桩：把 PNG/GIF/BMP 字节交给浏览器 <img> 解码。
 * 尺寸同步可用：直接解析图片头（PNG IHDR / GIF 逻辑屏幕 / BMP 头），
 * 未解析出时由 onload 回调补齐（下一帧生效，游戏 15FPS 全量重绘无感知）。
 * 未加载完成的图片 drawImage 由浏览器静默跳过——immediate-mode 下无残影。
 */
public class Image {
    int width;
    int height;
    JS.JSElement element;
    boolean decodeFailed;
    /** 高清版宽/逻辑宽（xBRZ 重制倍率），绘制时按逻辑矩形映射。 */
    int hdRatio = 1;

    private Image() {
    }

    public static Image createImage(String name) {
        InputStream is = Image.class.getResourceAsStream(name);
        if (is == null) {
            Host.log("createImage: resource not found: " + name);
            return empty();
        }
        try {
            return createImage(readAll(is), 0, -1);
        } catch (Exception e) {
            Host.log("createImage(" + name + ") failed: " + e);
            return empty();
        }
    }

    public static Image createImage(byte[] imageData, int imageOffset, int imageLength) {
        Image img = new Image();
        byte[] slice = imageData;
        int off = imageOffset;
        if (imageOffset != 0 || imageLength != imageData.length) {
            if (imageLength < 0) {
                imageLength = imageData.length - imageOffset;
            }
            slice = new byte[imageLength];
            System.arraycopy(imageData, imageOffset, slice, 0, imageLength);
            off = 0;
        }
        parseHeader(img, slice, off);
        String mime = sniffMime(slice, off);
        String b64 = base64Encode(slice, off, slice.length - off);
        String origUrl = "data:" + mime + ";base64," + b64;
        String url = origUrl;
        JS.JSElement el = JS.createElement("img");
        // 高清重制：命中构建期指纹表则替换为 xBRZ 放大版（绘制按逻辑矩形映射）
        String key = hdKey(slice, off);
        String hdUrl = key != null ? JS.hdDataUrl(key) : null;
        if (hdUrl != null) {
            url = hdUrl;
        } else if (JS.dumpMode() && key != null) {
            // 素材捕获模式（?dump=1）：把游戏解码后的真实图片按指纹倾倒给构建期
            JS.hdDump(key, url);
        }
        el.setSrc(url);
        el.addEventListener("load", new JS.JSFunc() {
            private Image owner;

            JS.JSFunc init(Image o) {
                this.owner = o;
                return this;
            }

            public void run() {
                if (owner.width <= 0) {
                    owner.width = owner.element.getWidth();
                    owner.height = owner.element.getHeight();
                }
                if (owner.width > 0) {
                    owner.hdRatio = Math.max(1, owner.element.getWidth() / owner.width);
                }
            }
        }.init(img));
        el.addEventListener("error", new JS.JSFunc() {
            private Image owner;
            private String fallback;

            JS.JSFunc init(Image o, String fb) {
                this.owner = o;
                this.fallback = fb;
                return this;
            }

            public void run() {
                // 高清版解码失败：回退原图，保证永不黑块
                if (owner.hdRatio != 1) {
                    owner.hdRatio = 1;
                    owner.element.setSrc(fallback);
                }
            }
        }.init(img, origUrl));
        img.element = el;
        return img;
    }

    /** 素材指纹：len_fnv1a32（与 make_hd.mjs 保持一致）。 */
    private static String hdKey(byte[] b, int off) {
        if (b == null || off >= b.length) {
            return null;
        }
        int h = (int) 2166136261L;
        for (int i = off; i < b.length; i++) {
            h ^= b[i] & 0xFF;
            h *= 16777619;
        }
        return (b.length - off) + "_" + Integer.toUnsignedString(h);
    }

    private static Image empty() {
        Image img = new Image();
        img.width = 1;
        img.height = 1;
        img.decodeFailed = true;
        return img;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    JS.JSElement getElement() {
        return element;
    }

    // ---------- 图片头解析（同步取尺寸） ----------

    private static void parseHeader(Image img, byte[] b, int off) {
        if (b == null || b.length - off < 24) {
            return;
        }
        if ((b[off] & 0xFF) == 0x89 && b[off + 1] == 'P' && b[off + 2] == 'N' && b[off + 3] == 'G') {
            img.width = i32be(b, off + 16);
            img.height = i32be(b, off + 20);
            return;
        }
        if (b[off] == 'G' && b[off + 1] == 'I' && b[off + 2] == 'F') { // GIF87a/89a
            img.width = u16le(b, off + 6);
            img.height = u16le(b, off + 8);
            return;
        }
        if ((b[off] & 0xFF) == 0xFF && (b[off + 1] & 0xFF) == 0xD8) { // JPEG
            int p = off + 2;
            while (p + 9 < b.length) {
                if ((b[p] & 0xFF) != 0xFF) {
                    p++;
                    continue;
                }
                int marker = b[p + 1] & 0xFF;
                if (marker >= 0xC0 && marker <= 0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) {
                    img.height = i16be(b, p + 5);
                    img.width = i16be(b, p + 7);
                    return;
                }
                int len = i16be(b, p + 2);
                if (len <= 0) {
                    return;
                }
                p += 2 + len;
            }
            return;
        }
        if ((b[off] & 0xFF) == 0xFF && (b[off + 1] & 0xFF) == 0xD8) {
            return;
        }
        if (b[off] == 'B' && b[off + 1] == 'M') { // BMP
            img.width = i32le(b, off + 18);
            img.height = i32le(b, off + 22);
        }
    }

    private static String sniffMime(byte[] b, int off) {
        if (b == null || b.length - off < 4) {
            return "image/png";
        }
        if ((b[off] & 0xFF) == 0x89 && b[off + 1] == 'P') {
            return "image/png";
        }
        if (b[off] == 'G' && b[off + 1] == 'I' && b[off + 2] == 'F') {
            return "image/gif";
        }
        if ((b[off] & 0xFF) == 0xFF && (b[off + 1] & 0xFF) == 0xD8) {
            return "image/jpeg";
        }
        if (b[off] == 'B' && b[off + 1] == 'M') {
            return "image/bmp";
        }
        return "image/png";
    }

    private static int i32be(byte[] b, int p) {
        return ((b[p] & 0xFF) << 24) | ((b[p + 1] & 0xFF) << 16) | ((b[p + 2] & 0xFF) << 8) | (b[p + 3] & 0xFF);
    }

    private static int i32le(byte[] b, int p) {
        return (b[p] & 0xFF) | ((b[p + 1] & 0xFF) << 8) | ((b[p + 2] & 0xFF) << 16) | ((b[p + 3] & 0xFF) << 24);
    }

    private static int i16be(byte[] b, int p) {
        return ((b[p] & 0xFF) << 8) | (b[p + 1] & 0xFF);
    }

    private static int u16le(byte[] b, int p) {
        return (b[p] & 0xFF) | ((b[p + 1] & 0xFF) << 8);
    }

    static byte[] readAll(InputStream is) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream(4096);
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            is.close();
            return bos.toByteArray();
        } catch (Exception e) {
            return new byte[0];
        }
    }

    public static String base64Encode(byte[] data, int off, int len) {
        final char[] table = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();
        int outLen = ((len + 2) / 3) * 4;
        StringBuilder sb = new StringBuilder(outLen);
        for (int i = 0; i < len; i += 3) {
            int remain = len - i;
            int b0 = data[off + i] & 0xFF;
            int b1 = remain > 1 ? data[off + i + 1] & 0xFF : 0;
            int b2 = remain > 2 ? data[off + i + 2] & 0xFF : 0;
            int trip = (b0 << 16) | (b1 << 8) | b2;
            sb.append(table[(trip >> 18) & 0x3F]);
            sb.append(table[(trip >> 12) & 0x3F]);
            sb.append(remain > 1 ? table[(trip >> 6) & 0x3F] : '=');
            sb.append(remain > 2 ? table[trip & 0x3F] : '=');
        }
        return sb.toString();
    }
}
