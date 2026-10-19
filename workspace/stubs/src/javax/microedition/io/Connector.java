package javax.microedition.io;

import host.Host;
import javax.wireless.messaging.DummyMessageConnection;

import java.io.IOException;

/**
 * MIDP Connector 桩。
 * 安全策略：sms://（SP 计费）与 http(s)://（Macrospace 上分）一律返回哑连接，
 * 计费/联网链路在此终结，字节码零改动。
 */
public class Connector {
    public static Connection open(String name) throws IOException {
        if (name == null) {
            throw new IOException("null connection name");
        }
        if (name.startsWith("sms://")) {
            Host.log("sms blocked: " + name);
            return new DummyMessageConnection();
        }
        if (name.startsWith("http://") || name.startsWith("https://")) {
            Host.log("http blocked: " + name);
            return new DummyMessageConnection();
        }
        throw new IOException("Unsupported scheme: " + name);
    }

    public static Connection open(String name, int mode) throws IOException {
        return open(name);
    }

    public static Connection open(String name, int mode, boolean timeouts) throws IOException {
        return open(name);
    }
}
