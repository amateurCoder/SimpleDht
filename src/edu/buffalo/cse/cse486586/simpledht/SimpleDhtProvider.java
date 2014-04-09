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

/**
 * SimpleDhtProvider contains all the implementation code
 * 
 * @author ankitsul
 * 
 */

public class SimpleDhtProvider extends ContentProvider {
	static final String TAG = SimpleDhtProvider.class.getSimpleName();
	static final int SERVER_PORT = 10000;

	/* Constants for content provider table columns */
	private static final String KEY_FIELD = "key";
	private static final String VALUE_FIELD = "value";

	private String portStr;
	private String myPort;

	private String predecessor;
	private String successor;

	List<PortHashObject> activePorts;

	/* Used in implementation of query operation */
	private boolean queryResponseReceived;
	private boolean allResponseReceived;
	private boolean resultReturned;
	private String responsePort;

	/* Used in implementation of delete operation */
	private boolean deleteResponseReceived;

	private MessageType messageTypeFlag;
	private String selectionGlobal;

	private Cursor responseCursor;
	private MatrixCursor resultCursorAll;
	private MatrixCursor partialResultCursor;

	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		if (selection.equals("*")) {
			responsePort = portStr;
			messageTypeFlag = MessageType.DELETE_REQUEST_ALL;
			deleteLocalFiles();
			cursorClient(null, null);
			while (!deleteResponseReceived) {
				// Wait until the response is received
			}
			// resetting it to false
			deleteResponseReceived = false;
			return 0;

		} else if (selection.equals("@")) {
			deleteLocalFiles();
		} else {
			if (isFileAvailable(selection)) {
				responsePort = portStr;
				getContext().deleteFile(selection);
				clientTask(portStr, MessageType.DELETE_REQUEST.toString(),
						selection, responsePort);
			} else {
				/* Forward it to successor */
				if (selectionArgs == null) {
					responsePort = portStr;
					messageTypeFlag = MessageType.DELETE_REQUEST;
					selectionGlobal = selection;
					sendRequest();

					while (!deleteResponseReceived) {
						// Wait until the response is received
					}
					// resetting it to false
					deleteResponseReceived = false;
					return 0;
					// If carried over request
				} else if (selectionArgs != null) {
					clientTask(portStr, MessageType.DELETE_REQUEST.toString(),
							selection, responsePort);
				}
			}
		}
		return 0;
	}

	/** Method to delete all files from the content provider */
	private void deleteLocalFiles() {
		File[] files = getAllFiles();
		if (files != null) {
			for (int i = 0; i < files.length; i++) {
				getContext().deleteFile(files[i].toString());
			}
		}
	}

	@Override
	public String getType(Uri uri) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		Log.v("insert", values.toString());
		String filename = null;
		filename = values.getAsString("key");

		String value = values.getAsString("value");
		// Check before inserting if it is the right place to insert
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
			} catch (NoSuchAlgorithmException e) {
				Log.e(TAG,
						"No such algorithm exception while creating SHA1 hash:"
								+ e.getMessage());
			}
			// To handle the wrapped DHT
			if (portStr.equals(activePorts.get(0).getPortNumber())) {
				if (comparisonPredecessor < 0 || comparisonNode <= 0) {
					insertLocally(filename, value);
				} else {
					/* Forward it to successor */
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

	/** Method to insert key locally */
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

	/** Method to move the request to the successor */
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
		deleteResponseReceived = false;
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
			Log.e(TAG,
					"IO Exception while creating server socket:"
							+ e.getMessage());
		}

		PortHashObject portHashObject = null;
		try {
			portHashObject = new PortHashObject(portStr, genHash(portStr));
		} catch (NoSuchAlgorithmException e) {
			Log.e(TAG, "No such algorithm exception while creating SHA1 hash:"
					+ e.getMessage());
		}

		if (!portStr.equals("5554")) {
			new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,
					MessageType.JOIN_REQUEST.toString());
		} else {
			// Adding 5554 as the default active port
			activePorts.add(portHashObject);
		}
		return false;
	}

	/** Class for asynchronous Server task */
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

		/** Method to read message from the stream */
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
					if (message.getMessageType().equals(
							MessageType.ACTIVE_PORTS_UPDATE)) {
						if (!portStr.equals("5554")) {
							activePorts = message.getActivePorts();
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
					} else if (message.getMessageType().equals(
							MessageType.INSERT)) {
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
						if (message.getCursorMap() != null) {
							partialResultCursor = convertMapToCursor(message
									.getCursorMap());
						}
						allResponseReceived = true;

						// Case where the response port is same as the current
						// port.
						if (message.getResponsePort().equals(portStr)) {
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
						deleteResponseReceived = true;
						if (!message.getResponsePort().equals(portStr)) {
							Uri mUri = buildUri("content",
									"edu.buffalo.cse.cse486586.simpledht.provider");
							ContentResolver mContentResolver = getContext()
									.getContentResolver();

							responsePort = message.getResponsePort();
							mContentResolver.delete(mUri, "*", null);

						}
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
							// Unicast the result back to "responsePort"
							messageTypeFlag = MessageType.QUERY_RESPONSE;
							cursorClient(resultCursor, null);
						}
					} else if (message.getMessageType().equals(
							MessageType.DELETE_REQUEST)) {
						// If not found then ask the successor.
						Log.d(TAG,
								"Request received from "
										+ message.getSenderPort());
						deleteResponseReceived = true;
						if (!message.getResponsePort().equals(portStr)) {
							Uri mUri = buildUri("content",
									"edu.buffalo.cse.cse486586.simpledht.provider");
							ContentResolver mContentResolver = getContext()
									.getContentResolver();

							responsePort = message.getResponsePort();
							String[] selectionArray = { "carriedOver" };

							mContentResolver.delete(mUri,
									message.getSelection(), selectionArray);

						}
					} else if (message.getMessageType().equals(
							MessageType.QUERY_RESPONSE)) {
						responseCursor = convertMapToCursor(message
								.getCursorMap());
						queryResponseReceived = true;
					}
				}
			}
		}

		/** Method to update the successor and predecessor pointers */
		private void setPredecessorAndSuccessor(String joiningNodePort) {
			String nodeToModify;
			String predecessor;
			String successor;
			try {
				PortHashObject portHashObject = new PortHashObject(
						joiningNodePort, genHash(joiningNodePort));
				activePorts.add(portHashObject);

			} catch (NoSuchAlgorithmException e) {
				Log.e(TAG,
						"No such algorithm exception while creating SHA1 hash:"
								+ e.getMessage());
			}

			// Sorting the ports according to the hash values
			Collections.sort(activePorts);

			new PortUpdateClientTask()
					.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
			for (int i = 0; i < activePorts.size(); i++) {
				if (activePorts.get(i).getPortNumber().equals(joiningNodePort)) {
					if (activePorts.size() > 2) {
						if (i == 0) {
							predecessor = activePorts.get(
									activePorts.size() - 1).getPortNumber();
							nodeToModify = activePorts.get(i).getPortNumber();
							successor = activePorts.get(i + 1).getPortNumber();
						} else if (i == (activePorts.size() - 1)) {
							predecessor = activePorts.get(i - 1)
									.getPortNumber();
							nodeToModify = activePorts.get(i).getPortNumber();
							successor = activePorts.get(0).getPortNumber();
						} else {
							predecessor = activePorts.get(i - 1)
									.getPortNumber();
							nodeToModify = activePorts.get(i).getPortNumber();
							successor = activePorts.get(i + 1).getPortNumber();
						}
						notifyNode(null, predecessor, nodeToModify);
						notifyNode(predecessor, nodeToModify, successor);
						notifyNode(nodeToModify, successor, null);
					} else if (activePorts.size() == 2) {
						if (i == 0) {
							predecessor = activePorts.get(
									activePorts.size() - 1).getPortNumber();
							nodeToModify = activePorts.get(i).getPortNumber();
							successor = activePorts.get(activePorts.size() - 1)
									.getPortNumber();
						} else {
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

		/**
		 * Class to update the node's successor and predecessor in case a new
		 * node join
		 */
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
					Log.e(TAG,
							"Host not known exception while creating socket:"
									+ e.getMessage());
				} catch (IOException e) {
					Log.e(TAG,
							"IO Exception while creating socket:"
									+ e.getMessage());
				}
				return null;
			}
		}

		/** Method to handle node join */
		private void notifyNode(String predecessor, String node,
				String successor) {
			Socket socket;
			ObjectOutputStream objectOutputStream;
			Message message = new Message();
			message.setMessageType(MessageType.NODE_PROPERTIES_MODIFICATION);
			message.setSenderPort(portStr);

			message.setPredecessor(predecessor);
			message.setSuccessor(successor);

			try {
				socket = new Socket(InetAddress.getByAddress(new byte[] { 10,
						0, 2, 2 }), Integer.parseInt(node) * 2);
				objectOutputStream = new ObjectOutputStream(
						socket.getOutputStream());
				objectOutputStream.writeObject(message);
				objectOutputStream.close();
			} catch (UnknownHostException e) {
				Log.e(TAG, "Host not known exception while creating socket:"
						+ e.getMessage());
			} catch (IOException e) {
				Log.e(TAG,
						"IO Exception while creating socket:" + e.getMessage());
			}
		}

		/** Method to create the Uri for the content provider */
		private Uri buildUri(String scheme, String authority) {
			Uri.Builder uriBuilder = new Uri.Builder();
			uriBuilder.authority(authority);
			uriBuilder.scheme(scheme);
			return uriBuilder.build();
		}

		/** Method to convert Map into a Cursor object */
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
				return getLocalCursor(columns);
			}
			if (!allResponseReceived) {
				allResponsePort = portStr;
			}
			messageTypeFlag = MessageType.QUERY_REQUEST_ALL;
			// Appending requester's own cursor
			MatrixCursor localCursor = getLocalCursor(columns);
			MatrixCursor finalCursor = concat(partialResultCursor, localCursor);
			partialResultCursor = null;
			if (!resultReturned) {
				cursorClient(finalCursor, allResponsePort);
			}

			while (!allResponseReceived) {
				// Wait until the response is received
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
				if (sortOrder == null) {
					responsePort = portStr;
					messageTypeFlag = MessageType.QUERY_REQUEST;
					selectionGlobal = selection;
					sendRequest();

					while (!queryResponseReceived) {
						// Wait until the response is received
					}
					// resetting it to false
					queryResponseReceived = false;
					return responseCursor;
					// If carried over request
				} else if (sortOrder.equals("carriedOver")) {
					clientTask(portStr, MessageType.QUERY_REQUEST.toString(),
							selection, responsePort);
				}
			}
		}
		return null;
	}

	/**
	 * Method to concatenate the local cursor with the received cursor from the
	 * predecessor
	 */
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

	/**
	 * Method to retrieve the cursor object containing all the data from the
	 * content provider
	 */
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
			cursor.close();
		}
		return cursor;
	}

	/** Method to retrieve all the files from the content provider */
	private File[] getAllFiles() {
		File currentDir = new File(System.getProperty("user.dir")
				+ "data/data/edu.buffalo.cse.cse486586.simpledht/files");
		return currentDir.listFiles();
	}

	/** Method to check if the requested key is present in the content provider */
	private boolean isFileAvailable(String filename) {
		File file = getContext().getFileStreamPath(filename);
		return file.exists();
	}

	/** Method to retrieve the value corresponding to the requested key */
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
			Log.e(TAG, "File not found exception:" + e.getMessage());
		} catch (IOException e) {
			Log.e(TAG, "IO Exception while creating socket:" + e.getMessage());
		}
		return value;
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection,
			String[] selectionArgs) {
		// TODO Auto-generated method stub
		return 0;
	}

	/** Method to send node join request to node 5554 */
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
					socket = new Socket(InetAddress.getByAddress(new byte[] {
							10, 0, 2, 2 }), 5554 * 2);
					objectOutputStream = new ObjectOutputStream(
							socket.getOutputStream());
					objectOutputStream.writeObject(message);
					objectOutputStream.close();
					socket.close();
				} catch (UnknownHostException e) {
					Log.e(TAG,
							"Host not known exception while creating socket:"
									+ e.getMessage());
				} catch (IOException e) {
					Log.e(TAG,
							"IO Exception while creating socket:"
									+ e.getMessage());
				}
			}
			return null;
		}
	}

	/** Method to send INSERT, QUERY_REQUEST and DELETE_REQUEST to the successor */
	private void clientTask(String... msgs) {
		Socket socket = null;
		ObjectOutputStream objectOutputStream;
		Message message = new Message();
		message.setSenderPort(msgs[0]);

		// Setting the type of request
		message.setMessageType(MessageType.valueOf(msgs[1]));

		if (message.getMessageType().equals(MessageType.INSERT)) {
			message.setKey(msgs[2]);
			message.setValue(msgs[3]);
		} else if ((message.getMessageType().equals(MessageType.QUERY_REQUEST))
				|| (message.getMessageType().equals(MessageType.DELETE_REQUEST))) {
			message.setSelection(msgs[2]);
			message.setResponsePort(msgs[3]);
		}

		try {
			socket = new Socket(InetAddress.getByAddress(new byte[] { 10, 0, 2,
					2 }), Integer.parseInt(successor) * 2);
			objectOutputStream = new ObjectOutputStream(
					socket.getOutputStream());
			objectOutputStream.writeObject(message);
			objectOutputStream.close();
			socket.close();
		} catch (IOException e) {
			Log.e(TAG, "IO Exception while creating socket:" + e.getMessage());
		}
	}

	/** Method to send request to the successor */
	public void sendRequest() {
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
			objectOutputStream = new ObjectOutputStream(
					socket.getOutputStream());
			objectOutputStream.writeObject(message);
			objectOutputStream.close();
			socket.close();
		} catch (IOException e) {
			Log.e(TAG, "IO Exception while creating socket:" + e.getMessage());
		}
	}

	/** Method to generate SHA-1 hash value */
	private String genHash(String input) throws NoSuchAlgorithmException {
		MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
		byte[] sha1Hash = sha1.digest(input.getBytes());
		Formatter formatter = new Formatter();
		for (byte b : sha1Hash) {
			formatter.format("%02x", b);
		}
		return formatter.toString();
	}

	/** Method to handle QUERY_REQUEST_ALL and DELETE_REQUEST_ALL */
	public void cursorClient(Cursor cursor, String allResponsePort) {
		Socket socket = null;
		ObjectOutputStream objectOutputStream;
		String destinationPort = responsePort;

		Message message = new Message();
		Map<String, String> cursorMap = null;
		if (null != cursor) {
			cursorMap = convertCursorToMap(cursor);
		}

		message.setCursorMap(cursorMap);
		message.setSenderPort(portStr);
		if (allResponsePort != null) {
			message.setResponsePort(allResponsePort);
		} else {
			message.setResponsePort(responsePort);
		}

		message.setMessageType(messageTypeFlag);

		if (messageTypeFlag.equals(MessageType.QUERY_REQUEST_ALL)
				|| messageTypeFlag.equals(MessageType.DELETE_REQUEST_ALL)) {
			destinationPort = successor;
		}

		try {
			socket = new Socket(InetAddress.getByAddress(new byte[] { 10, 0, 2,
					2 }), Integer.parseInt(destinationPort) * 2);

			objectOutputStream = new ObjectOutputStream(
					socket.getOutputStream());
			objectOutputStream.writeObject(message);
			objectOutputStream.close();
		} catch (IOException e) {
			Log.e(TAG, "IO Exception while creating socket:" + e.getMessage());
		}
	}

	/** Method to convert Cursor to a Map */
	private Map<String, String> convertCursorToMap(Cursor cursor) {
		Map<String, String> cursorMap = new HashMap<String, String>();
		while (cursor.moveToNext()) {
			cursorMap.put(cursor.getString(cursor.getColumnIndex("key")),
					cursor.getString(cursor.getColumnIndex("value")));
		}
		return cursorMap;
	}
}
