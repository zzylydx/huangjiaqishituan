package javax.wireless.messaging;

/** 哑消息连接：所有发送静默丢弃。 */
public class DummyMessageConnection implements MessageConnection {
    static class DummyTextMessage implements TextMessage {
        String address;
        String text;

        public void setAddress(String addr) {
            this.address = addr;
        }

        public String getAddress() {
            return address;
        }

        public void setPayloadText(String text) {
            this.text = text;
        }

        public String getPayloadText() {
            return text;
        }
    }

    public Message newMessage(String type) {
        return new DummyTextMessage();
    }

    public void send(Message msg) {
        // 计费终结点：静默丢弃
    }

    public void close() {
        // no-op
    }
}
