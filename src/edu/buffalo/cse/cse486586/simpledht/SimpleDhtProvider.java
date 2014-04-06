package edu.buffalo.cse.cse486586.simpledht;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.telephony.TelephonyManager;
import android.util.Log;

public class SimpleDhtProvider extends ContentProvider {
	static final String TAG = SimpleDhtProvider.class.getSimpleName();
	static final int SERVER_PORT = 10000;

	private static final String KEY_FIELD = "key";
	private static final String VALUE_FIELD = "value";

	private String portStr;
	private String myPort;

	private String predecessor;
	private String successor;

	List<PortHashObject> activePorts;

	private boolean queryResponseReceived;
	private boolean allResponseReceived;
	private boolean resultReturned;
	private String responsePort;

	// Variables for query
	private MessageType messageTypeFlag;
	private String selectionGlobal;

	private Cursor responseCursor;
	private MatrixCursor resultCursorAll;
	private MatrixCursor partialResultCursor;

	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public String getType(Uri uri) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		Log.v("insert", values.toString());
		/* Log.v(TAG, "Predecessor:" + predecessor + ",Sucessor:" + successor); */
		String filename = null;
		filename = values.getAsString("key");

		String value = values.getAsString("value");
		// insertLocally(filename, value);
		// Check before inserting if it is the right place to insert
		// In case where only this avd is active
		if (successor.equals(portStr) && predecessor.equals(portStr)) {
			insertLocally(filename, value);
			return null;
		} else {
			int comparisonPredecessor = 0;
			int comparisonNode = 0;
			try {
				comparisonPredecessor = genHash(predecessor).compareTo(
						genHash(filename));
				comparisonNode = genHash(filename).compareTo(genHash(portStr));
			} catch (NoSuchAlgorithmException e1) {
				e1.printStackTrace();
			}
			// To handle the wrapped DHT
			// if (portStr.equals("5562")) {
			if (portStr.equals(activePorts.get(0).getPortNumber())) {
				if (comparisonPredecessor < 0 || comparisonNode <= 0) {
					// Log.d(TAG, "IN 1");
					insertLocally(filename, value);
				} else {
					/* Forward it to successor */
					// Log.d(TAG, "IN 2");
					moveToSucessor(values);
				}
			} else {
				if (comparisonPredecessor < 0 && comparisonNode <= 0) {
					insertLocally(filename, value);
				} else {
					/* Forward it to successor */
					moveToSucessor(values);
				}
			}

		}
		return null;
	}

	private void insertLocally(String filename, String value) {
		Log.d(TAG, "Value inserted locally:" + filename + ":" + value);
		try {
			FileOutputStream fileOutputStream = getContext().openFileOutput(
					filename, Context.MODE_PRIVATE);
			fileOutputStream.write(value.getBytes());
			fileOutputStream.close();
		} catch (IOException e) {
			Log.e(TAG,
					"IO Exception while writing to the file:" + e.getMessage());
		}
	}

	private void moveToSucessor(ContentValues values) {
		Log.d(TAG,
				"Value moved to successor:" + successor + ":"
						+ values.getAsString("key") + ":"
						+ values.getAsString("value"));
		clientTask(portStr, MessageType.INSERT.toString(),
				values.getAsString("key"), values.getAsString("value"));
	}

	@Override
	public boolean onCreate() {
		queryResponseReceived = false;
		allResponseReceived = false;
		resultReturned = false;
		responsePort = null;

		TelephonyManager tel = (TelephonyManager) this.getContext()
				.getSystemService(Context.TELEPHONY_SERVICE);
		portStr = tel.getLine1Number().substring(
				tel.getLine1Number().length() - 4);

		myPort = String.valueOf((Integer.parseInt(portStr) * 2));

		activePorts = new ArrayList<PortHashObject>();
		predecessor = portStr;
		successor = portStr;

		try {
			ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
			new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,
					serverSocket);
		} catch (IOException e) {
			e.printStackTrace();
		}
		PortHashObject portHashObject = null;
		try {
			portHashObject = new PortHashObject(portStr, genHash(portStr));
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		if (!portStr.equals("5554")) {

			new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,
					MessageType.JOIN_REQUEST.toString());
			// setPredecessorAndSuccessor();
		} else {

			// Adding 5554 as the default active port
			activePorts.add(portHashObject);
			// Collections.sort(activePorts);
			// sendPortUpdate(activePorts);
			// new PortUpdateClientTask()
			// .executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
		}

		return false;
	}

	private class ServerTask extends AsyncTask<ServerSocket, String, Void> {
		@Override
		protected Void doInBackground(ServerSocket... sockets) {
			ServerSocket serverSocket = sockets[0];

			try {
				readMessage(serverSocket);
			} catch (IOException e) {
				Log.e(TAG,
						"IO Exception while reading the message from the stream:"
								+ e.getMessage());
			} catch (ClassNotFoundException e) {
				Log.e(TAG,
						"Class loader is unable to load the class:"
								+ e.getMessage());
			}
			return null;
		}

		private void readMessage(ServerSocket serverSocket) throws IOException,
				ClassNotFoundException {
			Message message = null;
			while (true) {
				Socket socket = serverSocket.accept();
				ObjectInputStream objectInputStream = new ObjectInputStream(
						socket.getInputStream());
				message = (Message) objectInputStream.readObject();
				objectInputStream.close();

				if (null != message) {
					Log.d(TAG, "Message Type:" + message.getMessageType());
					// Log.d(TAG, "Inside server block");
					if (message.getMessageType().equals(
							MessageType.ACTIVE_PORTS_UPDATE)) {
						if (!portStr.equals("5554")) {
							activePorts = message.getActivePorts();
							Log.d(TAG, "SIZE of Active ports received:"
									+ activePorts.size());
						}
					} else if (message.getMessageType().equals(
							MessageType.JOIN_REQUEST)) {
						Log.d(TAG,
								"Node join request from:"
										+ message.getSenderPort());
						setPredecessorAndSuccessor(message.getSenderPort());
					} else if (message.getMessageType().equals(
							MessageType.NODE_PROPERTIES_MODIFICATION)) {
						if (message.getPredecessor() != null) {
							predecessor = message.getPredecessor();
						}
						if (message.getSuccessor() != null) {
							successor = message.getSuccessor();
						}

						Log.v(TAG, "@@Predecessor:" + predecessor
								+ ",@@Sucessor:" + successor);

					} else if (message.getMessageType().equals(
							MessageType.INSERT)) {
						/*
						 * Log.d(TAG, "Value received:" + message.getKey() + ":"
						 * + message.getValue());
						 */
						// Create ContentValues object with the key and value
						// and then call insert() of this node
						ContentValues mContentValues = new ContentValues();
						mContentValues.put(KEY_FIELD, message.getKey());
						mContentValues.put(VALUE_FIELD, message.getValue());
						Uri mUri = buildUri("content",
								"edu.buffalo.cse.cse486586.simpledht.provider");
						ContentResolver mContentResolver = getContext()
								.getContentResolver();
						mContentResolver.insert(mUri, mContentValues);
					} else if (message.getMessageType().equals(
							MessageType.QUERY_REQUEST_ALL)) {
						// Add the local cursor value to the message and pass it
						// to the successor
						responsePort = message.getResponsePort();
						Log.d(TAG, "RESPONSE PORT:" + message.getResponsePort());
						if (message.getCursorMap() != null) {
							Log.d(TAG, "Cursor map size:"
									+ message.getCursorMap().size());
							partialResultCursor = convertMapToCursor(message
									.getCursorMap());
						}
						allResponseReceived = true;
						if (message.getResponsePort().equals(portStr)) {
							Log.d(TAG, "MSG2");
							resultCursorAll = convertMapToCursor(message
									.getCursorMap());
							resultReturned = true;

						} else {
							resultReturned = false;
							Uri mUri = buildUri("content",
									"edu.buffalo.cse.cse486586.simpledht.provider");
							ContentResolver mContentResolver = getContext()
									.getContentResolver();
							mContentResolver.query(mUri, null, "*", null, null);
						}

					} else if (message.getMessageType().equals(
							MessageType.DELETE_REQUEST_ALL)) {
						// TODO: Delete all the values from the local provider
					} else if (message.getMessageType().equals(
							MessageType.QUERY_REQUEST)) {
						Log.d(TAG,
								"Request received from "
										+ message.getSenderPort());
						Uri mUri = buildUri("content",
								"edu.buffalo.cse.cse486586.simpledht.provider");
						ContentResolver mContentResolver = getContext()
								.getContentResolver();

						responsePort = message.getResponsePort();
						Cursor resultCursor = mContentResolver.query(mUri,
								null, message.getSelection(), null,
								"carriedOver");

						// Checking if the cursor is not empty
						if (resultCursor != null && resultCursor.getCount() > 0) {
							// Send the result back to "myPortReceived" -
							// Unicast
							Log.d(TAG, "Got result");
							messageTypeFlag = MessageType.QUERY_RESPONSE;
							cursorClient(resultCursor, null/* "singleQuery" */);

						}
					} else if (message.getMessageType().equals(
							MessageType.DELETE_REQUEST)) {
						// TODO: call delete of this avd
						// If not found then ask the next successor.

					} else if (message.getMessageType().equals(
							MessageType.QUERY_RESPONSE)) {
						// Receive all the query response
						// Set a flag to determine whether it was a single
						// response or from all the avds (in case of *)
						Log.d(TAG, "Received response");
						/*
						 * for (Map.Entry<String, String> entry : message
						 * .getCursorMap().entrySet()) { Log.d(TAG, "KKey : " +
						 * entry.getKey() + " VValue : " + entry.getValue()); }
						 */
						responseCursor = convertMapToCursor(message
								.getCursorMap());
						// Log.d(TAG, "Response Cursor");
						while (responseCursor.moveToNext()) {
							Log.d(TAG,
									"Response Cursor:"
											+ responseCursor.getString(responseCursor
													.getColumnIndex("key"))
											+ responseCursor.getString(responseCursor
													.getColumnIndex("value")));
						}
						queryResponseReceived = true;
						/*
						 * Log.d(TAG, "Query response Value:" +
						 * queryResponseReceived);
						 */
					}
				}
			}
		}

		private void setPredecessorAndSuccessor(String joiningNodePort) {
			String nodeToModify;
			String predecessor;
			String successor;
			// Log.v(TAG, "Active port size before:" + activePorts.size());
			try {
				PortHashObject portHashObject = new PortHashObject(
						joiningNodePort, genHash(joiningNodePort));
				activePorts.add(portHashObject);

			} catch (NoSuchAlgorithmException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			// Log.v(TAG, "Active port size after:" + activePorts.size());
			Collections.sort(activePorts);
			Log.d(TAG, "SIZE OF ACTIVE PORTs:" + activePorts.size());
			new PortUpdateClientTask()
					.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
			for (int i = 0; i < activePorts.size(); i++) {
				if (activePorts.get(i).getPortNumber().equals(joiningNodePort)) {
					if (activePorts.size() > 2) {
						if (i == 0) {
							// Log.v(TAG, "SETTING 1");
							predecessor = activePorts.get(
									activePorts.size() - 1).getPortNumber();
							nodeToModify = activePorts.get(i).getPortNumber();
							successor = activePorts.get(i + 1).getPortNumber();
						} else if (i == (activePorts.size() - 1)) {
							// Log.v(TAG, "SETTING 2");
							predecessor = activePorts.get(i - 1)
									.getPortNumber();
							nodeToModify = activePorts.get(i).getPortNumber();
							successor = activePorts.get(0).getPortNumber();
						} else {
							// Log.v(TAG, "SETTING 3");
							predecessor = activePorts.get(i - 1)
									.getPortNumber();
							nodeToModify = activePorts.get(i).getPortNumber();
							successor = activePorts.get(i + 1).getPortNumber();
						}
						// Update the affected nodes
						notifyNode(null, predecessor, nodeToModify);
						notifyNode(predecessor, nodeToModify, successor);
						notifyNode(nodeToModify, successor, null);
					} else if (activePorts.size() == 2) {
						if (i == 0) {
							// Log.v(TAG, "SETTING 11");
							predecessor = activePorts.get(
									activePorts.size() - 1).getPortNumber();
							nodeToModify = activePorts.get(i).getPortNumber();
							successor = activePorts.get(activePorts.size() - 1)
									.getPortNumber();
						} else {
							// Log.v(TAG, "SETTING 12");
							predecessor = activePorts.get(0).getPortNumber();
							nodeToModify = activePorts.get(i).getPortNumber();
							successor = activePorts.get(0).getPortNumber();
						}
						notifyNode(nodeToModify, predecessor, nodeToModify);
						notifyNode(predecessor, nodeToModify, successor);
					}
				}
			}

		}

		private class PortUpdateClientTask extends
				AsyncTask<String, Void, Void> {

			@Override
			protected Void doInBackground(String... arg0) {
				Socket socket;
				ObjectOutputStream objectOutputStream;
				Message message = new Message();
				message.setMessageType(MessageType.ACTIVE_PORTS_UPDATE);
				message.setActivePorts(activePorts);
				try {
					for (int i = 0; i < activePorts.size(); i++) {
						socket = new Socket(
								InetAddress.getByAddress(new byte[] { 10, 0, 2,
										2 }), Integer.parseInt(activePorts.get(
										i).getPortNumber()) * 2);
						objectOutputStream = new ObjectOutputStream(
								socket.getOutputStream());
						objectOutputStream.writeObject(message);
						objectOutputStream.close();
						socket.close();
					}
				} catch (UnknownHostException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				return null;
			}

		}

		private void notifyNode(String predecessor, String node,
				String successor) {
			Socket socket;
			ObjectOutputStream objectOutputStream;
			Message message = new Message();
			message.setMessageType(MessageType.NODE_PROPERTIES_MODIFICATION);
			message.setSenderPort(portStr);

			message.setPredecessor(predecessor);
			message.setSuccessor(successor);

			// if (message.getMessageType().equals(MessageType.JOIN_REQUEST)) {
			try {
				// Log.d(TAG, "Sending Node modify request");
				socket = new Socket(InetAddress.getByAddress(new byte[] { 10,
						0, 2, 2 }), Integer.parseInt(node) * 2);
				objectOutputStream = new ObjectOutputStream(
						socket.getOutputStream());
				objectOutputStream.writeObject(message);
				objectOutputStream.close();
//				socket.close();
			} catch (UnknownHostException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			// }
		}

		private Uri buildUri(String scheme, String authority) {
			Uri.Builder uriBuilder = new Uri.Builder();
			uriBuilder.authority(authority);
			uriBuilder.scheme(scheme);
			return uriBuilder.build();
		}

		private MatrixCursor convertMapToCursor(Map<String, String> cursorMap) {
			String[] columns = { "key", "value" };
			MatrixCursor cursor = new MatrixCursor(columns);
			for (Map.Entry<String, String> entry : cursorMap.entrySet()) {
				Object[] row = new Object[cursor.getColumnCount()];

				row[cursor.getColumnIndex("key")] = entry.getKey();
				row[cursor.getColumnIndex("value")] = entry.getValue();

				cursor.addRow(row);
			}
			cursor.close();
			return cursor;
		}
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {
		Log.v(TAG, "query:" + selection);

		String[] columns = { "key", "value" };
		StringBuffer value = new StringBuffer();
		MatrixCursor cursor = new MatrixCursor(columns);
		Object[] row = new Object[cursor.getColumnCount()];
		if (selection.equals("*")) {
			String allResponsePort = null;
			if (successor.equals(portStr) && predecessor.equals(portStr)) {
				// Log.d(TAG, "Inside *");
				return getLocalCursor(columns);
			}
			if (!allResponseReceived) {
				allResponsePort = portStr;
			}
			messageTypeFlag = MessageType.QUERY_REQUEST_ALL;
			// Adding the requester own cursor
			MatrixCursor localCursor = getLocalCursor(columns);
			MatrixCursor finalCursor = concat(partialResultCursor, localCursor);
			// sendQuery();
			partialResultCursor = null;
			if (!resultReturned) {
				cursorClient(finalCursor, allResponsePort);
			}

			while (!allResponseReceived) {
				// Wait until the response is received
				// Log.d(TAG, "Infinite");
				if (resultReturned) {
					break;
				}
			}
			// resetting it to false
			allResponseReceived = false;
			resultReturned = false;
			return resultCursorAll;

		} else if (selection.equals("@")) {
			return getLocalCursor(columns);
		} else {
			if (isFileAvailable(selection)) {
				value = getValue(selection);
				row[cursor.getColumnIndex("key")] = selection;
				row[cursor.getColumnIndex("value")] = value;
				cursor.addRow(row);
				cursor.close();
				return cursor;
			} else if (!isFileAvailable(selection)) {
				/* Forward it to successor */
				// Checks if the query is a fresh one, myPortAlreadySet will
				// be false.

				if (sortOrder == null) {
					responsePort = portStr;
					messageTypeFlag = MessageType.QUERY_REQUEST;
					selectionGlobal = selection;
					sendQuery();

					while (!queryResponseReceived) {
						// Wait until the response is received
					}
					// resetting it to false
					queryResponseReceived = false;
					return responseCursor;
					// If carried over
				} else if (sortOrder.equals("carriedOver")) {
					clientTask(portStr, MessageType.QUERY_REQUEST.toString(),
							selection, responsePort);
				}
				// return responseCursor;

			}
		}

		return null;
	}

	private MatrixCursor concat(MatrixCursor partialResultCursor,
			MatrixCursor localCursor) {
		if (null == partialResultCursor) {
			return localCursor;
		}

		MatrixCursor result = partialResultCursor;

		while (localCursor.moveToNext()) {
			Object[] row = new Object[localCursor.getColumnCount()];
			row[localCursor.getColumnIndex("key")] = localCursor
					.getString(localCursor.getColumnIndex("key"));
			row[localCursor.getColumnIndex("value")] = localCursor
					.getString(localCursor.getColumnIndex("value"));
			result.addRow(row);
		}
		result.close();
		return result;
	}

	private MatrixCursor getLocalCursor(String[] columns) {
		MatrixCursor cursor = new MatrixCursor(columns);
		Object[] row = new Object[cursor.getColumnCount()];
		StringBuffer value;
		File[] files = getAllFiles();
		if (files != null) {
			for (int i = 0; i < files.length; i++) {
				value = getValue(files[i].getName());
				Log.d(TAG, "KEY,VALUE:" + files[i] + ":" + value);

				row[cursor.getColumnIndex("key")] = files[i].getName();
				row[cursor.getColumnIndex("value")] = value;
				cursor.addRow(row);
			}
			// Log.d(TAG, "Fetched everything");
			cursor.close();
			Log.d(TAG, "Cursor Count from local:" + cursor.getCount());

		}
		return cursor;
	}

	private File[] getAllFiles() {
		File currentDir = new File(System.getProperty("user.dir")
				+ "data/data/edu.buffalo.cse.cse486586.simpledht/files");
		return currentDir.listFiles();
	}

	private boolean isFileAvailable(String filename) {
		File file = getContext().getFileStreamPath(filename);
		return file.exists();
	}

	private StringBuffer getValue(String selection) {
		int ch;
		StringBuffer value = new StringBuffer();
		FileInputStream fileInputStream;
		try {
			fileInputStream = getContext().openFileInput(selection);

			while ((ch = fileInputStream.read()) != -1) {
				value.append((char) ch);
			}
			fileInputStream.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return value;
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection,
			String[] selectionArgs) {
		// TODO Auto-generated method stub
		return 0;
	}

	/* To send node join request */
	private class ClientTask extends AsyncTask<String, Void, Void> {

		@Override
		protected Void doInBackground(String... msgs) {
			Socket socket;
			ObjectOutputStream objectOutputStream;
			Message message = new Message();
			message.setMessageType(MessageType.valueOf(msgs[0]));
			message.setSenderPort(portStr);
			if (message.getMessageType().equals(MessageType.JOIN_REQUEST)) {
				try {
					// Log.d(TAG, "Sending Node join request");
					socket = new Socket(InetAddress.getByAddress(new byte[] {
							10, 0, 2, 2 }), 5554 * 2);
					objectOutputStream = new ObjectOutputStream(
							socket.getOutputStream());
					objectOutputStream.writeObject(message);
					objectOutputStream.close();
					socket.close();
				} catch (UnknownHostException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			return null;

		}
	}

	private void clientTask(String... msgs) {
		Socket socket = null;
		ObjectOutputStream objectOutputStream;
		Message message = new Message();
		message.setSenderPort(msgs[0]);

		// Setting the type of request
		message.setMessageType(MessageType.valueOf(msgs[1]));

		if (message.getMessageType().equals(MessageType.INSERT)) {
			// Log.d(TAG, "ENUM VALUE:" + message.getMessageType());
			message.setKey(msgs[2]);
			message.setValue(msgs[3]);
			Log.d(TAG, "Message Sent:" + msgs[0] + ":" + successor + ":"
					+ msgs[2] + ":" + msgs[3]);
		}
		// Message type is QUERY_REQUEST or DELETE_REQUEST, selection
		// denotes the file whose request is made

		else if ((message.getMessageType().equals(MessageType.QUERY_REQUEST))
				|| (message.getMessageType().equals(MessageType.DELETE_REQUEST
						.toString()))) {
			message.setSelection(msgs[2]);
			message.setResponsePort(msgs[3]);
		}

		try {
			socket = new Socket(InetAddress.getByAddress(new byte[] { 10, 0, 2,
					2 }), Integer.parseInt(successor) * 2);

			Log.d(TAG, "Type of message:" + msgs[1]);

			objectOutputStream = new ObjectOutputStream(
					socket.getOutputStream());
			objectOutputStream.writeObject(message);
			objectOutputStream.close();
			socket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		// return null;
	}

	public void sendQuery() {
		// Log.d(TAG, "Inside Send Query");
		Socket socket = null;
		ObjectOutputStream objectOutputStream;
		Message message = new Message();
		message.setSenderPort(portStr);
		message.setMessageType(messageTypeFlag);

		message.setSelection(selectionGlobal);
		message.setResponsePort(responsePort);

		try {
			socket = new Socket(InetAddress.getByAddress(new byte[] { 10, 0, 2,
					2 }), Integer.parseInt(successor) * 2);

			Log.d(TAG, "Type of message:" + message.getMessageType());

			objectOutputStream = new ObjectOutputStream(
					socket.getOutputStream());
			objectOutputStream.writeObject(message);
			objectOutputStream.close();
			socket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	private String genHash(String input) throws NoSuchAlgorithmException {
		MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
		byte[] sha1Hash = sha1.digest(input.getBytes());
		Formatter formatter = new Formatter();
		for (byte b : sha1Hash) {
			formatter.format("%02x", b);
		}
		return formatter.toString();
	}

	public void cursorClient(Cursor cursor, String allResponsePort) {
		// Unicast to "myPortReceived"
		Socket socket = null;
		ObjectOutputStream objectOutputStream;
		String destinationPort = responsePort;

		Message message = new Message();
		Log.d(TAG, "Cursor size while sending:" + cursor.getCount());
		Map<String, String> cursorMap = convertCursorToMap(cursor);
		Log.d(TAG, "Cursor Map size while sending:" + cursorMap.size());
		message.setCursorMap(cursorMap);
		message.setSenderPort(portStr);
		if (allResponsePort != null) {
			message.setResponsePort(allResponsePort);
		} else /* if (("singleQuery").equals(allResponsePort)) */{
			message.setResponsePort(responsePort);
		}

		message.setMessageType(messageTypeFlag);

		if (messageTypeFlag.equals(MessageType.QUERY_REQUEST_ALL)) {
			destinationPort = successor;
		}

		try {
			Log.d(TAG, "QUERY_ALL request sent:" + destinationPort);
			socket = new Socket(InetAddress.getByAddress(new byte[] { 10, 0, 2,
					2 }), Integer.parseInt(destinationPort) * 2);

			objectOutputStream = new ObjectOutputStream(
					socket.getOutputStream());
			objectOutputStream.writeObject(message);
			objectOutputStream.close();
			// socket.close();

			// responsePort = null;
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	private Map<String, String> convertCursorToMap(Cursor cursor) {
		Map<String, String> cursorMap = new HashMap<String, String>();
		while (cursor.moveToNext()) {
			cursorMap.put(cursor.getString(cursor.getColumnIndex("key")),
					cursor.getString(cursor.getColumnIndex("value")));
		}
		return cursorMap;
	}

}
