package edu.buffalo.cse.cse486586.simpledht;

public class PortHashObject implements Comparable<PortHashObject> {

	private String portNumber;
	private String hashedPortNumber;

	public PortHashObject(String portNumber, String hashedPortNumber) {
		this.portNumber = portNumber;
		this.hashedPortNumber = hashedPortNumber;
	}

	public String getPortNumber() {
		return portNumber;
	}

	public void setPortNumber(String portNumber) {
		this.portNumber = portNumber;
	}

	public String getHashedPortNumber() {
		return hashedPortNumber;
	}

	public void setHashedPortNumber(String hashedPortNumber) {
		this.hashedPortNumber = hashedPortNumber;
	}

	@Override
	public int compareTo(PortHashObject another) {
		String hashedValue = ((PortHashObject) another).getHashedPortNumber(); 
		 
		//ascending order
		return this.hashedPortNumber.compareTo(hashedValue);
	}
}
