package org.dreamwork.embedded.mqtt.data;

public class MqttConfig {
	public String name;

	public String url;

	public String username;

	public String password;

	public String clientId;

	public Integer timeout;

	public Integer interval;

	public boolean autoReconnect;

	public String getName () {
		return name;
	}

	public void setName (String name) {
		this.name = name;
	}

	public String getUrl () {
		return url;
	}

	public void setUrl (String url) {
		this.url = url;
	}

	public String getUsername () {
		return username;
	}

	public void setUsername (String username) {
		this.username = username;
	}

	public String getPassword () {
		return password;
	}

	public void setPassword (String password) {
		this.password = password;
	}

	public String getClientId () {
		return clientId;
	}

	public void setClientId (String clientId) {
		this.clientId = clientId;
	}

	public Integer getTimeout () {
		return timeout;
	}

	public void setTimeout (Integer timeout) {
		this.timeout = timeout;
	}

	public Integer getInterval () {
		return interval;
	}

	public void setInterval (Integer interval) {
		this.interval = interval;
	}
}