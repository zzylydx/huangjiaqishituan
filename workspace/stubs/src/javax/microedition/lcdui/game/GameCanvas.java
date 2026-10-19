package javax.microedition.lcdui.game;

import host.HostInput;
import javax.microedition.lcdui.Canvas;

/** MIDP GameCanvas 桩。本游戏仅继承 + super.getGameAction 等透传；getKeyStates 未用但仍实现。 */
public class GameCanvas extends Canvas {
    public static final int UP_PRESSED = 2;
    public static final int LEFT_PRESSED = 4;
    public static final int RIGHT_PRESSED = 32;
    public static final int DOWN_PRESSED = 128;
    public static final int FIRE_PRESSED = 256;
    public static final int GAME_A_PRESSED = 512;
    public static final int GAME_B_PRESSED = 1024;
    public static final int GAME_C_PRESSED = 2048;
    public static final int GAME_D_PRESSED = 4096;

    public GameCanvas(boolean suppressKeyEvents) {
        // 网页版无键盘事件抑制概念
    }

    /** 本游戏为 immediate-mode，不使用 GameCanvas 缓冲绘制。 */
    public void paint(javax.microedition.lcdui.Graphics g) {
        // no-op
    }

    public int getKeyStates() {
        return HostInput.keyStates();
    }

    public void flushGraphics() {
        // immediate-mode，不使用缓冲
    }

    public void flushGraphics(int x, int y, int width, int height) {
        // 同上
    }
}
