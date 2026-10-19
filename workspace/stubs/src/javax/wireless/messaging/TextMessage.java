package javax.wireless.messaging;

public interface TextMessage extends Message {
    void setPayloadText(String text);

    String getPayloadText();
}
