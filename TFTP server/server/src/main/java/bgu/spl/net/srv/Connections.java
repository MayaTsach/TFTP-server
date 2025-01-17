package bgu.spl.net.srv;


public interface Connections<T> {

    boolean connect(int connectionId, ConnectionHandler<T> handler);

    boolean send(int connectionId, T msg);

    void disconnect(int connectionId);
}
