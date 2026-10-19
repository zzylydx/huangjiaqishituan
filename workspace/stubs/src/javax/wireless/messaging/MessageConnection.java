package javax.wireless.messaging;

/** 无线消息桩接口（计费链路类型面）。 */
public interface MessageConnection extends javax.microedition.io.Connection {
    Message newMessage(String type);

    void send(Message msg) throws java.io.IOException;
}
