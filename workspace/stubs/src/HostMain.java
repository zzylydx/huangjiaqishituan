import host.Host;

/**
 * 网页版引导入口（默认包——游戏混淆类全在默认包，命名包无法引用，故同置于此）。
 * GameMidlet.startApp 内部会创建 j（Canvas+主循环）并 new Thread(j).start()
 * （TeaVM 绿线程），主循环自然运转。零反射。
 */
public final class HostMain {
    private HostMain() {
    }

    public static void main(String[] args) {
        Host.init();
        try {
            GameMidlet midlet = new GameMidlet();
            midlet.boot();
            if (Host.DEBUG) {
                Host.log("boot() returned normally");
            }
        } catch (Throwable t) {
            host.js.JS.error("boot failed: " + t);
        }
    }
}
