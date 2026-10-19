package javax.microedition.media;

public interface Player {
    void setLoopCount(int count);

    void start();

    void stop();

    void close();

    void deallocate();
}
