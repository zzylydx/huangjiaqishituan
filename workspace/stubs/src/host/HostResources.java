package host;

import org.teavm.classlib.ResourceSupplier;
import org.teavm.classlib.ResourceSupplierContext;

/**
 * 声明需要内联进 game.js 的游戏资源（编译期 base64 嵌入，运行期经
 * Class.getResourceAsStream 读取）。注册于 META-INF/services。
 */
public class HostResources implements ResourceSupplier {
    private static final String[] RESOURCES = {
            "128X116logo-2.png",
            "240X320logo.png",
            "240_320.pak",
            "32_32.png",
            "HaKuNa.png",
            "QQBig.png",
            "bg.mid",
            "main.mid",
            "lang.dat",
            "mm.png",
            "setting.dat",
            "setting.dat_11",
            "icons/15_15.png",
            "icons/16_16.png",
            "icons/26_26.png",
            "icons/32_32.png",
    };

    @Override
    public String[] supplyResources(ResourceSupplierContext context) {
        // 只列出 classpath 上真实存在的资源
        int count = 0;
        for (int i = 0; i < RESOURCES.length; i++) {
            if (context.getClassLoader().getResource(RESOURCES[i]) != null) {
                count++;
            }
        }
        String[] out = new String[count];
        int j = 0;
        for (int i = 0; i < RESOURCES.length; i++) {
            if (context.getClassLoader().getResource(RESOURCES[i]) != null) {
                out[j++] = RESOURCES[i];
            }
        }
        return out;
    }
}
