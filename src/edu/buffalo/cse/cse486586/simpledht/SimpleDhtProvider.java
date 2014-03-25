package edu.buffalo.cse.cse486586.simpledht;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;

import android.content.ContentProvider;
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
	private String predecessor;
	private String successor;

	private String myPort;
	
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
				deleteFile(selection);
			}
		}
		return 0;
	}

	private File[] getAllFiles() {
		File currentDir = new File(System.getProperty("user.dir"));
		return currentDir.listFiles();
	}

	private void deleteFile(String selection) {
		File file = null;
		try {
			file = new File(genHash(selection));
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
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

		try {
			FileOutputStream fileOutputStream = getContext().openFileOutput(
					filename, Context.MODE_PRIVATE);
			fileOutputStream.write(value.getBytes());
			fileOutputStream.close();
		} catch (IOException e) {
			Log.e(TAG,
					"IO Exception while writing to the file:" + e.getMessage());
		}
		return uri;
	}

	@Override
	public boolean onCreate() {
		TelephonyManager tel = (TelephonyManager) this.getContext()
				.getSystemService(Context.TELEPHONY_SERVICE);
		String portStr = tel.getLine1Number().substring(
				tel.getLine1Number().length() - 4);

		myPort = String.valueOf((Integer.parseInt(portStr) * 2));
		
		String nodeId = "";
		try {
			nodeId = genHash(portStr);
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// Setting predecessor and successor of a node
		predecessor = Integer.toString((Integer.parseInt(portStr) - 2) % 5564);
		successor = Integer.toString((Integer.parseInt(portStr) + 2) % 5564);

		// Setting predecessor and successor appropriately
		if (predecessor.equals("5552")) {
			predecessor = "5562";
		}

		if (successor.equals("0")) {
			successor = "5554";
		}
		return false;
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {
		Log.v("query", selection);

		String[] columns = { "key", "value" };
		StringBuffer value = new StringBuffer();
		MatrixCursor cursor = new MatrixCursor(columns);
		Object[] row = new Object[cursor.getColumnCount()];

		if (selection.equals("*")) {
			new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,
                    myPort,
                    MessageType.REQUEST.toString());
		} else if (selection.equals("@")) {
			File[] files = getAllFiles();
			for (int i = 0; i < files.length; i++) {
				value = getValue(files[i].getName());

				row[cursor.getColumnIndex("key")] = files[i].getName();
				row[cursor.getColumnIndex("value")] = value;
				cursor.addRow(row);
			}
		} else {
			value = getValue(selection);

			row[cursor.getColumnIndex("key")] = selection;
			row[cursor.getColumnIndex("value")] = value;
			cursor.addRow(row);
		}
		return cursor;
	}

	private StringBuffer getValue(String selection) {
		int ch;
		StringBuffer value = new StringBuffer();
		FileInputStream fileInputStream;
		try {
			fileInputStream = getContext().openFileInput(genHash(selection));

			while ((ch = fileInputStream.read()) != -1) {
				value.append((char) ch);
			}
			fileInputStream.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
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
		protected Void doInBackground(String... arg0) {
			//Multicast if the message type is "REQUEST"
			// TODO Auto-generated method stub
			return null;
		}
		
	}
}
