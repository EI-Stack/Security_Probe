package oam.security.model.webSocket.client;

import java.io.IOException;
import java.net.URI;

import javax.websocket.ClientEndpoint;
import javax.websocket.ContainerProvider;
import javax.websocket.OnMessage;
import javax.websocket.Session;

@ClientEndpoint
public class WebSocketClientConfig {
	Session session = null;

	public WebSocketClientConfig(URI endpointURI) {
		try {
			session = ContainerProvider.getWebSocketContainer().connectToServer(this, endpointURI);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@OnMessage
	public void onMessage(String message) {
		System.out.println(message);
	}

	public void sendMessage(String message) {
		try {
			this.session.getBasicRemote().sendText(message);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
