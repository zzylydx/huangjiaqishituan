package host;

import javax.microedition.lcdui.Canvas;

/** 输入注入：JS 虚拟按键/触摸/物理键盘 -> Canvas.keyPressed/keyReleased。 */
public final class HostInput {
    private static boolean up, down, left, right, fire;

    private HostInput() {
    }

    public static void dispatch(int keyCode, boolean isDown) {
        switch (keyCode) {
            case -1: up = isDown; break;
            case -2: down = isDown; break;
            case -3: left = isDown; break;
            case -4: right = isDown; break;
            case -5: fire = isDown; break;
            default:
        }
        Canvas.dispatchKey(keyCode, isDown);
    }

    public static int keyStates() {
        int s = 0;
        if (up) s |= javax.microedition.lcdui.game.GameCanvas.UP_PRESSED;
        if (down) s |= javax.microedition.lcdui.game.GameCanvas.DOWN_PRESSED;
        if (left) s |= javax.microedition.lcdui.game.GameCanvas.LEFT_PRESSED;
        if (right) s |= javax.microedition.lcdui.game.GameCanvas.RIGHT_PRESSED;
        if (fire) s |= javax.microedition.lcdui.game.GameCanvas.FIRE_PRESSED;
        return s;
    }
}
