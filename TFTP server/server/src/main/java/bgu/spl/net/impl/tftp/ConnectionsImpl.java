package bgu.spl.net.impl.tftp;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import bgu.spl.net.srv.ConnectionHandler;
import bgu.spl.net.srv.Connections;

public class ConnectionsImpl<T> implements Connections<T> {

    private ConcurrentMap<Integer, ConnectionHandler<T>> activeUsers;
    private ConcurrentMap<Integer, String> loggedIn;

    public ConnectionsImpl() {
        this.activeUsers = new ConcurrentHashMap<>();
        this.loggedIn = new ConcurrentHashMap<>();
    }

    @Override
    public boolean connect(int connectionId, ConnectionHandler<T> handler) {
        if(activeUsers.containsKey(connectionId)) // The user is already connected
            return false;
        activeUsers.put(Integer.valueOf(connectionId), handler);
        return true;
    }

    @Override
    public boolean send(int connectionId, T msg) {
        if(!activeUsers.containsKey(connectionId)) // The user isn't connected
            return false;
            
        // The user is connected
        ConnectionHandler<T> handler = activeUsers.get(connectionId);
        handler.send(msg);
        return true;
    }

    @Override
    public void disconnect(int connectionId) {
        activeUsers.remove(connectionId);
        loggedIn.remove(connectionId);
    }

    public ConcurrentMap<Integer, ConnectionHandler<T>> getActiveUsers() {
        return this.activeUsers;
    }

    /**
     * Sends a broadcast packet to all logged-in users.
     *
     * @param bcastPacket The broadcast packet to be sent to all logged-in users.
     *                    This packet will be sent to each user individually.
     *                    It should be of the appropriate type expected by the send method.
     */
    public void sendBcast(T bcastPacket) {
        for (Integer id : loggedIn.keySet()) {
            send(id, bcastPacket);
        }
    }

    public boolean logIn(Integer Id, String userName){
        if(loggedIn.containsKey(Id)) // Already exists
            return false;
        loggedIn.put(Id, userName);
        return true;
    }

    public boolean isLoggedIn(String userName, int id){
        if(loggedIn.containsKey(id))
            return true;
        return loggedIn.containsValue(userName);
    }
     
}
