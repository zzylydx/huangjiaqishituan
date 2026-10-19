package javax.microedition.midlet;

import host.Host;

/**
 * MIDP MIDlet 桩。平台行为：
 * - platformRequest 返回 false（剔除 SP 短信计费与 Macrospace 上分等一切外部请求）
 * - notifyDestroyed 只是标记（网页环境无退出概念，主循环自然收敛）
 */
public abstract class MIDlet {
    protected MIDlet() {
    }

    protected abstract void startApp();

    /**
     * 宿主引导桥：MIDlet 子类的 startApp 是 protected，外部（默认包 HostMain）
     * 无法直调，由此公开桥接。
     */
    public final void boot() {
        startApp();
    }

    protected abstract void pauseApp();

    protected abstract void destroyApp(boolean unconditional);

    /**
     * MIDP: 请求销毁。网页版仅记录。
     */
    protected final void notifyDestroyed() {
        Host.log("MIDlet.notifyDestroyed called");
        Host.halted = true;
    }

    /**
     * MIDP: 平台属性。游戏用于 Macrospace 高分榜配置，全部返回 null（代码已判空）。
     */
    protected final String getAppProperty(String key) {
        return null;
    }

    /**
     * MIDP: 平台请求（URL/SMS）。安全桩：一律拒绝且不报错。
     */
    public final boolean platformRequest(String location) {
        Host.log("platformRequest blocked: " + location);
        return false;
    }

    protected final void resumeRequest() {
        // no-op
    }

    public final void notifyPaused() {
        // no-op
    }
}
