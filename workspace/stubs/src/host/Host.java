package host;

import host.js.JS;
import host.js.JS.PadHandler;
import javax.microedition.lcdui.Graphics;

/**
 * 宿主桥：绑定画布、注入输入、初始化音频。
 * 缩放由 JS 侧按视口选最大整数倍（像素完美）：iPhone 15 Pro 上为 4x，
 * 画布 backing = 240x4 x 320x4 = 960x1280 设备像素（CSS 320x426.7pt）。
 */
public final class Host {
    public static final int LOGICAL_W = 240;
    public static final int LOGICAL_H = 320;
    /** 实际值由 JS 侧按视口计算（window.__gscale），运行中可变。 */
    public static int SCALE = 3;

    public static boolean attached;
    public static boolean halted;
    /** 置 true 可在控制台看到 setCurrent/paint 等诊断日志（发布版关闭）。 */
    public static boolean DEBUG = false;

    private static Graphics graphics;
    private static JS.JSCtx mainCtx;

    private Host() {
    }

    public static void init() {
        JS.JSCanvas canvas = JS.gameCanvas();
        mainCtx = canvas.getContext("2d");
        SCALE = Math.max(1, JS.hostScale());
        mainCtx.setTransform(SCALE, 0, 0, SCALE, 0, 0);
        mainCtx.setImageSmoothingEnabled(false);
        graphics = new Graphics(mainCtx);

        // 虚拟按键/键盘/触摸统一经 JS 侧 window.__pad(code,down) 转入
        JS.registerPad(new PadHandler() {
            public void call(int keyCode, boolean down) {
                try {
                    HostAudio.ensureResumed();
                    HostInput.dispatch(keyCode, down);
                } catch (Throwable t) {
                    JS.error("pad dispatch: " + t);
                }
            }
        });

        // 视口变化（旋转/桌面拖窗口）：JS 已更新画布尺寸，这里同步 Graphics 变换
        JS.registerResize(new JS.ResizeHandler() {
            public void call(int scale) {
                if (scale >= 1 && scale != Host.SCALE) {
                    Host.SCALE = scale;
                    try {
                        mainCtx.setTransform(scale, 0, 0, scale, 0, 0);
                    } catch (Throwable t) {
                        JS.error("resize transform: " + t);
                    }
                }
            }
        });

        HostAudio.init();
        JS.log("host init done, scale=" + Host.SCALE);
    }

    public static void attach() {
        attached = true;
    }

    public static Graphics graphics() {
        return graphics;
    }

    /** 文本测量上下文：与绘制用同一画布，保证 measureText 与渲染一致。 */
    public static JS.JSCtx textCtx() {
        return mainCtx;
    }

    public static void halt() {
        halted = true;
    }

    public static void log(String s) {
        JS.log(s);
    }
}
