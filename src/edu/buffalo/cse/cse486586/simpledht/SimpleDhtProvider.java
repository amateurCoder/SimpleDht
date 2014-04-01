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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
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

	private String predecessor;
	private String successor;

	private String nodeId;

	private String responsePort;

	private String selectionGlobal;

	private String portStr;
	private String myPort;

	private boolean queryResponseReceived;
	private Cursor responseCursor;

	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		if (selection != null) {
			// Deleting all the files in DHT
			if (selection.equals("*")) {

			}
			// Deleting all the files in local avd
			else if (selection.equals("@")) {
				File[] files = getAllFiles();
				for (int i = 0; i < files.length; i++) {
					deleteFile(files[i].getName());
				}
			}
			// When only requested file needs to be deleted
			else {
				try {
					/* If file not present then send the request to successor */
					if (isFileAvailable(genHash(selection))) {
						deleteFile(genHash(selection));
					} else {
						/* Forward request to successor */
						new ClientTask().executeOnExecutor(
								AsyncTask.SERIAL_EXECUTOR, myPort,
								MessageType.DELETE_REQUEST.toString(),
								selection);
					}

				} catch (NoSuchAlgorithmException e) {
					e.printStackTrace();
				}
			}
		}
		return 0;
	}

	private File[] getAllFiles() {
		File currentDir = new File(System.getProperty("user.dir")
				+ "data/data/edu.buffalo.cse.cse486586.simpledht/files");
		return currentDir.listFiles();
	}

	private void deleteFile(String selection) {
		File file = null;
		file = new File(selection);
		file.delete();
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
		try {
			filename = genHash(values.getAsString("key"));
		} catch (NoSuchAlgorithmException e1) {
			e1.printStackTrace();
		}

		String value = values.getAsString("value");

		// Check before inserting if it is the right place to insert
		int comparisonPredecessor = 0;
		int comparisonNode = 0;
		try {
			comparisonPredecessor = genHash(predecessor).compareTo(filename);
			comparisonNode = filename.compareTo(genHash(portStr));
		} catch (NoSuchAlgorithmException e1) {
			e1.printStackTrace();
		}
		// If filename is greater than predecessor and less than/equal to
		// itself, then it should be inserted in the current node
		// Boundary case when the hash value turns around
		if (portStr.equals("5562")) {
			if (comparisonPredecessor < 0 || comparisonNode <= 0) {
				Log.d(TAG, "IN 1");
				insertLocally(filename, value);
			} else {
				/* Forward it to successor */
				Log.d(TAG, "IN 2");
				moveToSucessor(values);
			}
		} else {
			if (comparisonPredecessor < 0 && comparisonNode <= 0) {
				Log.d(TAG, "IN 3");
				insertLocally(filename, value);
			} else {
				/* Forward it to successor */
				Log.d(TAG, "IN 4");
				moveToSucessor(values);
			}
		}
		return uri;
	}

	private void moveToSucessor(ContentValues values) {
		Log.d(TAG, "Value moved to successor");
		new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, myPort,
				MessageType.INSERT.toString(), values.getAsString("key"),
				values.getAsString("value"));
		Log.d(TAG, "IN 5");
	}

	private void insertLocally(String filename, String value) {
		Log.d(TAG, "Value inserted locally");
		try {
			FileOutputStream fileOutputStream = getContext().openFileOutput(
					filename, Context.MODE_PRIVATE);
			fileOutputStream.write(value.getBytes());
			fileOutputStream.close();
			Log.d(TAG, "IN 6");
		} catch (IOException e) {
			Log.e(TAG,
					"IO Exception while writing to the file:" + e.getMessage());
		}
	}

	@Override
	public boolean onCreate() {
		queryResponseReceived = false;
		responsePort = null;

		TelephonyManager tel = (TelephonyManager) this.getContext()
				.getSystemService(Context.TELEPHONY_SERVICE);
		portStr = tel.getLine1Number().substring(
				tel.getLine1Number().length() - 4);

		myPort = String.valueOf((Integer.parseInt(portStr) * 2));

		Log.d(TAG, "MY PORT:" + portStr);
		try {
			nodeId = genHash(portStr);
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}

		// Setting predecessor and successor of a node
		setPredecessorAndSuccessor(portStr);

		try {
			ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
			new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,
					serverSocket);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return false;
	}

	public void setPredecessorAndSuccessor(String portStr) {
		try {
			PortHashObject hash5554 = new PortHashObject("5554",
					genHash("5554"));
			PortHashObject hash5556 = new PortHashObject("5556",
					genHash("5556"));
			PortHashObject hash5558 = new PortHashObject("5558",
					genHash("5558"));
			PortHashObject hash5560 = new PortHashObject("5560",
					genHash("5560"));
			PortHashObject hash5562 = new PortHashObject("5562",
					genHash("5562"));

			// Sorting the list of hashes
			List<PortHashObject> hashes = Arrays.asList(hash5554, hash5556,
					hash5558, hash5560, hash5562);
			Collections.sort(hashes);

			for (int i = 0; i < hashes.size(); i++) {
				Log.d(TAG, "##" + hashes.get(i).getPortNumber() + "#"
						+ hashes.get(i).getHashedPortNumber());
				if (hashes.get(i).getPortNumber().equals(portStr)) {
					if (i == 0) {
						successor = hashes.get(1).getPortNumber();
						predecessor = hashes.get(4).getPortNumber();
					} else if (i == 4) {
						successor = hashes.get(0).getPortNumber();
						predecessor = hashes.get(3).getPortNumber();
					} else {
						successor = hashes.get(i + 1).getPortNumber();
						predecessor = hashes.get(i - 1).getPortNumber();
					}
				}
			}
			Log.d(TAG, "Successor:" + successor);
			Log.d(TAG, "Predecessor:" + predecessor);

		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
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
					Log.d(TAG, "Inside server block");
					if (message.getMessageType().equals(MessageType.INSERT)) {
						Log.d(TAG, "Value received:" + message.getKey() + ":"
								+ message.getValue());
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
						// TODO: Retrieve all the values from the local provider
						// and send it back to the sender
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
							new CursorClientTask().executeOnExecutor(
									AsyncTask.SERIAL_EXECUTOR, resultCursor);

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
						for (Map.Entry<String, String> entry : message
								.getCursorMap().entrySet()) {
							Log.d(TAG, "KKey : " + entry.getKey()
									+ " VValue : " + entry.getValue());
						}
						responseCursor = convertMapToCursor(message
								.getCursorMap());
						Log.d(TAG, "Response Cursor");
						while (responseCursor.moveToNext()) {
							Log.d(TAG,
									responseCursor.getString(responseCursor
											.getColumnIndex("key"))
											+ responseCursor.getString(responseCursor
													.getColumnIndex("value")));
						}
						queryResponseReceived = true;
						Log.d(TAG, "Query response Value:"
								+ queryResponseReceived);
					}
				}
			}
		}

		private Cursor convertMapToCursor(Map<String, String> cursorMap) {
			String[] columns = { "key", "value" };
			MatrixCursor cursor = new MatrixCursor(columns);
			for (Map.Entry<String, String> entry : cursorMap.entrySet()) {
				Object[] row = new Object[cursor.getColumnCount()];

				row[cursor.getColumnIndex("key")] = entry.getKey();
				row[cursor.getColumnIndex("value")] = entry.getValue();

				cursor.addRow(row);
			}
			return cursor;
		}

		private Uri buildUri(String scheme, String authority) {
			Uri.Builder uriBuilder = new Uri.Builder();
			uriBuilder.authority(authority);
			uriBuilder.scheme(scheme);
			return uriBuilder.build();
		}
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {
		Log.v("query", selection);

		String[] columns = { "key", "value" };
		StringBuffer value = new StringBuffer();
		MatrixCursor cursor = new MatrixCursor(columns);
		Object[] row = new Object[cursor.getColumnCount()];

		selectionGlobal = selection;

		if (selection.equals("*")) {
			// new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,
			// myPort, MessageType.QUERY_REQUEST_ALL.toString());
		} else if (selection.equals("@")) {
			File[] files = getAllFiles();
			for (int i = 0; i < files.length; i++) {
				value = getValue(files[i].getName());
				Log.d(TAG, "VALUE:" + value);

				row[cursor.getColumnIndex("key")] = files[i].getName();
				row[cursor.getColumnIndex("value")] = value;
				cursor.addRow(row);
				return cursor;
			}
		} else {
			Log.d(TAG, "Inside step1");
			try {
				// If value is not present, then forward the request to
				// successor

				if (isFileAvailable(genHash(selection))) {
					Log.d(TAG, "Inside step2");
					value = getValue(genHash(selection));
					row[cursor.getColumnIndex("key")] = selection;
					row[cursor.getColumnIndex("value")] = value;
					cursor.addRow(row);
					return cursor;
				} else if (!isFileAvailable(genHash(selection))) {
					Log.d(TAG, "Inside step3");
					/* Forward it to successor */
					// Checks if the query is a fresh one, myPortAlreadySet will
					// be false.

					if (sortOrder == null) {
						Log.d(TAG, "Inside step4");
						responsePort = myPort;
						/*
						 * new ClientTask().executeOnExecutor(
						 * AsyncTask.SERIAL_EXECUTOR, myPort,
						 * MessageType.QUERY_REQUEST.toString(), selection,
						 * responsePort);
						 */
						(new Thread(new ClientThread())).start();

						Log.d(TAG, "Inside step5");

						while (!queryResponseReceived) {
							// Wait until the response is received
						}
						// resetting it to false
						queryResponseReceived = false;
						return responseCursor;
						// If carried over
					} else if (sortOrder.equals("carriedOver")) {
						Log.d(TAG, "Inside step6");
						new ClientTask().executeOnExecutor(
								AsyncTask.SERIAL_EXECUTOR, myPort,
								MessageType.QUERY_REQUEST.toString(),
								selection, responsePort);
					}

					Log.d(TAG, "ELSE5");
					// return responseCursor;

				}
			} catch (NoSuchAlgorithmException e) {
				e.printStackTrace();
			}
		}
		return null;
		// return cursor;
	}

	public class ClientThread implements Runnable {

		@Override
		public void run() {
			Log.d(TAG, "Inside Run");
			Socket socket = null;
			ObjectOutputStream objectOutputStream;
			Message message = new Message();
			message.setSenderPort(myPort);
			message.setMessageType(MessageType.QUERY_REQUEST);

			message.setSelection(selectionGlobal);
			message.setResponsePort(responsePort);

			try {
				socket = new Socket(InetAddress.getByAddress(new byte[] { 10,
						0, 2, 2 }), Integer.parseInt(successor) * 2);

				Log.d(TAG, "Type of message:");

				objectOutputStream = new ObjectOutputStream(
						socket.getOutputStream());
				objectOutputStream.writeObject(message);
				objectOutputStream.close();
				socket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}

		}

	}

	/* Checking if the file is available in the current node */
	private boolean isFileAvailable(String filename) {
		int comparisonPredecessor = 0;
		int comparisonNode = 0;
		try {
			comparisonPredecessor = genHash(predecessor).compareTo(filename);
			comparisonNode = filename.compareTo(genHash(portStr));
		} catch (NoSuchAlgorithmException e1) {
			e1.printStackTrace();
		}
		// Boundary case when the hash value turns around
		if (portStr.equals("5562")) {
			if (comparisonPredecessor < 0 || comparisonNode <= 0) {
				return true;
			}
		} else {
			if (comparisonPredecessor < 0 && comparisonNode <= 0) {
				return true;
			}
		}
		return false;
		// File file = getContext().getFileStreamPath(filename);
		// return file.exists();
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

	private String genHash(String input) throws NoSuchAlgorithmException {
		MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
		byte[] sha1Hash = sha1.digest(input.getBytes());
		Formatter formatter = new Formatter();
		for (byte b : sha1Hash) {
			formatter.format("%02x", b);
		}
		return formatter.toString();
	}

	private class ClientTask extends AsyncTask<String, Void, Void> {

		@Override
		protected Void doInBackground(String... msgs) {
			Log.d(TAG, "Inside client1");
			Socket socket = null;
			ObjectOutputStream objectOutputStream;
			Message message = new Message();
			message.setSenderPort(msgs[0]);

			// Setting the type of request
			message.setMessageType(MessageType.valueOf(msgs[1]));

			Log.d(TAG, "Inside client");

			if (message.getMessageType().equals(MessageType.INSERT)) {
				Log.d(TAG, "ENUM VALUE VALUE:" + message.getMessageType());
				message.setKey(msgs[2]);
				message.setValue(msgs[3]);
				Log.d(TAG, "Message Sent:" + msgs[0] + ":" + successor + ":"
						+ msgs[2] + ":" + msgs[3]);
			}
			// Message type is QUERY_REQUEST or DELETE_REQUEST, selection
			// denotes the file whose request is made
			else if ((message.getMessageType()
					.equals(MessageType.QUERY_REQUEST))
					|| (message.getMessageType()
							.equals(MessageType.DELETE_REQUEST.toString()))) {
				message.setSelection(msgs[2]);
				message.setResponsePort(msgs[3]);
			}

			try {
				socket = new Socket(InetAddress.getByAddress(new byte[] { 10,
						0, 2, 2 }), Integer.parseInt(successor) * 2);

				Log.d(TAG, "Type of message:" + msgs[1]);

				objectOutputStream = new ObjectOutputStream(
						socket.getOutputStream());
				objectOutputStream.writeObject(message);
				objectOutputStream.close();
				socket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}

			return null;
		}
	}

	private class CursorClientTask extends AsyncTask<Cursor, Void, Void> {

		@Override
		protected Void doInBackground(Cursor... cursors) {
			// Unicast to "myPortReceived"
			Socket socket = null;
			ObjectOutputStream objectOutputStream;
			Message message = new Message();
			Map<String, String> cursorMap = convertCursorToMap(cursors[0]);
			message.setCursorMap(cursorMap);
			message.setMessageType(MessageType.QUERY_RESPONSE);

			try {
				Log.d(TAG, "My POrt received:" + responsePort);
				socket = new Socket(InetAddress.getByAddress(new byte[] { 10,
						0, 2, 2 }), Integer.parseInt(responsePort));

				objectOutputStream = new ObjectOutputStream(
						socket.getOutputStream());
				objectOutputStream.writeObject(message);
				objectOutputStream.close();
				socket.close();

				// responsePort = null;
			} catch (IOException e) {
				e.printStackTrace();
			}
			return null;
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
}
