package host;

import host.js.JS;
import host.js.JS.PadHandler;
import javax.microedition.lcdui.Graphics;

/**
 * 宿主桥：绑定画布、注入输入、初始化音频。
 * 缩放由 JS 侧按视口计算：CSS 尺寸取整数、backing = 整数CSS × dpr（设备像素 1:1，
 * 零重采样），Java 侧保存 X/Y 两个浮点缩放供 Graphics 变换。
 */
public final class Host {
    public static final int LOGICAL_W = 240;
    public static final int LOGICAL_H = 320;
    /** 实际值由 JS 侧按视口计算（window.__gscaleX/Y），运行中可变。 */
    public static double SCALE_X = 3;
    public static double SCALE_Y = 4;

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
        SCALE_X = JS.hostScaleX();
        SCALE_Y = JS.hostScaleY();
        mainCtx.setTransform(SCALE_X, 0, 0, SCALE_Y, 0, 0);
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

        // 游戏区触摸直通（FullCn 原生分区）
        JS.registerPointer(new JS.PointerHandler() {
            public void call(int x, int y, boolean down) {
                try {
                    HostAudio.ensureResumed();
                    HostInput.dispatchPointer(x, y, down);
                } catch (Throwable t) {
                    JS.error("pointer dispatch: " + t);
                }
            }
        });

        // 视口变化（旋转/工具栏收起/桌面拖窗口）：JS 已重设画布尺寸，这里同步 Graphics 变换
        JS.registerResize(new JS.ResizeHandler() {
            public void call(double scaleX, double scaleY) {
                if (scaleX >= 1 && scaleY >= 1 && (scaleX != Host.SCALE_X || scaleY != Host.SCALE_Y)) {
                    Host.SCALE_X = scaleX;
                    Host.SCALE_Y = scaleY;
                    try {
                        mainCtx.setTransform(scaleX, 0, 0, scaleY, 0, 0);
                    } catch (Throwable t) {
                        JS.error("resize transform: " + t);
                    }
                }
            }
        });

        HostAudio.init();
        JS.log("host init done, scale=" + Host.SCALE_X + "x" + Host.SCALE_Y);
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
