/*
 *              bitlet - Simple bittorrent library
 *
 * Copyright (C) 2008 Alessandro Bahgat Shehata, Daniele Castagna
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * Alessandro Bahgat Shehata - ale dot bahgat at gmail dot com
 * Daniele Castagna - daniele dot castagna at gmail dot com
 *
 */

package org.bitlet.wetorrent.peer;

import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.bitlet.wetorrent.Torrent;
import org.bitlet.wetorrent.util.thread.InterruptableTasksThread;
import org.bitlet.wetorrent.util.thread.ThreadTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IncomingPeerListener extends InterruptableTasksThread {
	private static Logger log = LoggerFactory.getLogger(IncomingPeerListener.class);

    ServerSocket serverSocket;
    private Map<ByteBuffer, Torrent> torrents = new HashMap<ByteBuffer, Torrent>();
    private Set<TorrentPeer> dispatchingPeers = new HashSet<TorrentPeer>();
    private int port;
    private int receivedConnection = 0;

    public IncomingPeerListener(int port) {

        log.debug("Binding incoming server socket");
        while (port < 65535 && serverSocket == null) {
            try {
                serverSocket = new ServerSocket(port);
            } catch (Exception e) {
            	log.debug("Cannot bind port " + port);
                port++;
            }
        }

        if (serverSocket == null) {

        	log.error("Cannot bind the incoming socket");
            return;
        }
        
        log.info("Listing for incoming peer on port " + port);
        this.port = port;

        final IncomingPeerListener incomingPeerListener = this;
        addTask(new ThreadTask() {

            public boolean execute() throws Exception {

                try {
                    Socket socket = serverSocket.accept();

                    receivedConnection++;
                    TorrentPeer peer = new TorrentPeer(socket, incomingPeerListener);
                    dispatchingPeers.add(peer);
                    peer.start();
                } catch (SocketException e) {
                    return false;
                }
                return true;
            }

            public void interrupt() {
                try {
                    serverSocket.close();
                } catch (Exception e) {
                }
            }

            public void exceptionCought(Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        });


    }

    public int getPort() {
        return port;
    }

    public int getReceivedConnection() {
        return receivedConnection;
    }

    public synchronized void register(Torrent torrent) {
        torrents.put(ByteBuffer.wrap(torrent.getMetafile().getInfoSha1()), torrent);
    }

    public synchronized void unregister(Torrent torrent) {
        torrents.remove(ByteBuffer.wrap(torrent.getMetafile().getInfoSha1()));
    }

    public synchronized void peer(TorrentPeer dispatchingPeer) {
        dispatchingPeers.add(dispatchingPeer);
    }

    public synchronized boolean dispatchPeer(TorrentPeer dispatchingPeer, byte[] infoSha1) {
        dispatchingPeers.remove(dispatchingPeer);
        Torrent torrent = torrents.get(ByteBuffer.wrap(infoSha1));
        if (torrent != null) {
            dispatchingPeer.setPeersManager(torrent.getPeersManager());
            torrent.getPeersManager().offer(dispatchingPeer);
            return true;
        } else {
            return false;
        }
    }

    public void interrupt() {
        super.interrupt();
        
        log.info("Interrupting " + this);
        for (TorrentPeer p : dispatchingPeers) {
            p.interrupt();
        }
    }

    public synchronized void removePeer(TorrentPeer peer) {
        dispatchingPeers.remove(peer);
    }
    
    @Override
    public String toString() {
    	return "IncomingPeerListener (" + this.serverSocket + ")";
    }
    
    
}


