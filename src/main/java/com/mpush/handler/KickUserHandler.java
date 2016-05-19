package com.mpush.handler;

import com.mpush.api.ClientListener;
import com.mpush.api.Logger;
import com.mpush.api.connection.Connection;
import com.mpush.api.protocol.Packet;
import com.mpush.client.ClientConfig;
import com.mpush.message.KickUserMessage;
import com.mpush.util.DefaultLogger;

/**
 * Created by ohun on 2016/1/23.
 */
public final class KickUserHandler extends BaseMessageHandler<KickUserMessage> {
	private static final Logger logger = new DefaultLogger(KickUserHandler.class);

    @Override
    public KickUserMessage decode(Packet packet, Connection connection) {
        return new KickUserMessage(packet, connection);
    }

    @Override
    public void handle(KickUserMessage message) {
        logger.d("<<< receive an kickUser message=%s", message);
        ClientListener listener = ClientConfig.I.getClientListener();
        listener.onKickUser(message.deviceId, message.userId);
    }
}
