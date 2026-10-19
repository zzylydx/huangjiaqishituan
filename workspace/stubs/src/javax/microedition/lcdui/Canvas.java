package javax.microedition.lcdui;

import host.Host;

/**
 * MIDP Canvas 桩。
 * 关键语义：本游戏用 immediate-mode——j.k() 每帧调 repaint() + serviceRepaints()，
 * serviceRepaints 同步执行 paint(Graphics)（即 j.paint -> 场景 f.f(g)）。
 * 输入由宿主注入：HostInput.dispatch -> keyPressed/keyReleased（FullCn 覆盖后进 j.KEYPRESSED）。
 */
public abstract class Canvas extends Displayable {
    // 设备键码
    public static final int KEY_UP = -1;
    public static final int KEY_DOWN = -2;
    public static final int KEY_LEFT = -3;
    public static final int KEY_RIGHT = -4;
    public static final int KEY_FIRE = -5;
    public static final int KEY_NUM0 = 48;
    public static final int KEY_NUM1 = 49;
    public static final int KEY_NUM2 = 50;
    public static final int KEY_NUM3 = 51;
    public static final int KEY_NUM4 = 52;
    public static final int KEY_NUM5 = 53;
    public static final int KEY_NUM6 = 54;
    public static final int KEY_NUM7 = 55;
    public static final int KEY_NUM8 = 56;
    public static final int KEY_NUM9 = 57;
    public static final int KEY_STAR = 42;
    public static final int KEY_POUND = 35;

    // 游戏动作
    public static final int UP = 1;
    public static final int LEFT = 2;
    public static final int RIGHT = 5;
    public static final int DOWN = 6;
    public static final int FIRE = 8;
    public static final int GAME_A = 9;
    public static final int GAME_B = 10;
    public static final int GAME_C = 11;
    public static final int GAME_D = 12;

    static Canvas current;
    private boolean fullScreen;
    private boolean dirty;

    public abstract void paint(Graphics g);

    protected void keyPressed(int keyCode) {
    }

    protected void keyReleased(int keyCode) {
    }

    protected void keyRepeated(int keyCode) {
    }

    protected void pointerPressed(int x, int y) {
    }

    protected void pointerReleased(int x, int y) {
    }

    protected void pointerDragged(int x, int y) {
    }

    public void repaint() {
        dirty = true;
    }

    public void repaint(int x, int y, int width, int height) {
        dirty = true;
    }

    public final void serviceRepaints() {
        if (dirty && Host.attached) {
            dirty = false;
            if (Host.DEBUG && ++paintCount <= 3) {
                Host.log("serviceRepaints -> paint #" + paintCount + " on " + getClass().getName());
            }
            paint(Host.graphics());
        }
    }

    private int paintCount;

    public void setFullScreenMode(boolean mode) {
        this.fullScreen = mode;
    }

    public boolean isShown() {
        return Host.attached;
    }

    public int getGameAction(int keyCode) {
        switch (keyCode) {
            case KEY_UP:
                return UP;
            case KEY_DOWN:
                return DOWN;
            case KEY_LEFT:
                return LEFT;
            case KEY_RIGHT:
                return RIGHT;
            case KEY_FIRE:
                return FIRE;
            default:
                return 0;
        }
    }

    public int getKeyCode(int gameAction) {
        switch (gameAction) {
            case UP:
                return KEY_UP;
            case DOWN:
                return KEY_DOWN;
            case LEFT:
                return KEY_LEFT;
            case RIGHT:
                return KEY_RIGHT;
            case FIRE:
                return KEY_FIRE;
            default:
                return 0;
        }
    }

    public String getKeyName(int keyCode) {
        switch (keyCode) {
            case KEY_UP: return "Up";
            case KEY_DOWN: return "Down";
            case KEY_LEFT: return "Left";
            case KEY_RIGHT: return "Right";
            case KEY_FIRE: return "Select";
            case KEY_NUM0: return "0";
            case KEY_NUM1: return "1";
            case KEY_NUM2: return "2";
            case KEY_NUM3: return "3";
            case KEY_NUM4: return "4";
            case KEY_NUM5: return "5";
            case KEY_NUM6: return "6";
            case KEY_NUM7: return "7";
            case KEY_NUM8: return "8";
            case KEY_NUM9: return "9";
            case KEY_STAR: return "*";
            case KEY_POUND: return "#";
            default: return String.valueOf(keyCode);
        }
    }

    public static void dispatchKey(int keyCode, boolean down) {
        Canvas c = current;
        if (c == null) {
            return;
        }
        if (down) {
            c.keyPressed(keyCode);
        } else {
            c.keyReleased(keyCode);
        }
    }
}
