package javax.microedition.lcdui;

import host.Host;
import javax.microedition.midlet.MIDlet;

/** MIDP Display 桩：setCurrent 即绑定宿主画布。 */
public final class Display {
    private static final Display INSTANCE = new Display();

    private Display() {
    }

    public static Display getDisplay(MIDlet m) {
        return INSTANCE;
    }

    public void setCurrent(Displayable next) {
        if (Host.DEBUG) {
            Host.log("Display.setCurrent: " + (next == null ? "null" : next.getClass().getName()));
            if (next instanceof Form) {
                Form f = (Form) next;
                Host.log("  Form.title = " + f.getTitle());
                for (int i = 0; i < f.size(); i++) {
                    javax.microedition.lcdui.Item it = f.get(i);
                    if (it instanceof StringItem) {
                        Host.log("  item[" + i + "] = " + ((StringItem) it).getText());
                    }
                }
            }
        }
        if (next instanceof Canvas) {
            Canvas.current = (Canvas) next;
        }
        Host.attach();
    }

    public Displayable getCurrent() {
        return Canvas.current;
    }

    public void callSerially(Runnable r) {
        // 绿线程环境：直接执行足够（本游戏未用）
        r.run();
    }

    public boolean isColor() {
        return true;
    }

    public int numColors() {
        return 1 << 24;
    }
}
