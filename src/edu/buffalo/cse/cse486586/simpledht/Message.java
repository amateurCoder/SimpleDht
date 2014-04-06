package edu.buffalo.cse.cse486586.simpledht;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import android.database.Cursor;

public class Message implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	MessageType messageType;
	String senderPort;
	
	String predecessor;
	String successor;
	
	List<PortHashObject> activePorts;
	
	public List<PortHashObject> getActivePorts() {
		return activePorts;
	}

	public void setActivePorts(List<PortHashObject> activePorts) {
		this.activePorts = activePorts;
	}

	public String getPredecessor() {
		return predecessor;
	}

	public void setPredecessor(String predecessor) {
		this.predecessor = predecessor;
	}

	public String getSuccessor() {
		return successor;
	}

	public void setSuccessor(String successor) {
		this.successor = successor;
	}

	String responsePort;

	public String getResponsePort() {
		return responsePort;
	}

	public void setResponsePort(String responsePort) {
		this.responsePort = responsePort;
	}

	/* For insert operation */
	String key;
	String value;

	/* For query request */
	String selection;
	
	boolean firstRequest;

	/* Required for cursor response */
	Map<String, String> cursorMap;

	public boolean isFirstRequest() {
		return firstRequest;
	}

	public void setFirstRequest(boolean firstRequest) {
		this.firstRequest = firstRequest;
	}
	
	public Map<String, String> getCursorMap() {
		return cursorMap;
	}

	public void setCursorMap(Map<String, String> cursorMap) {
		this.cursorMap = cursorMap;
	}

	public String getSelection() {
		return selection;
	}

	public void setSelection(String selection) {
		this.selection = selection;
	}

	public MessageType getMessageType() {
		return messageType;
	}

	public String getKey() {
		return key;
	}

	public void setKey(String key) {
		this.key = key;
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}

	public void setMessageType(MessageType messageType) {
		this.messageType = messageType;
	}

	public String getSenderPort() {
		return senderPort;
	}

	public void setSenderPort(String senderPort) {
		this.senderPort = senderPort;
	}
}
