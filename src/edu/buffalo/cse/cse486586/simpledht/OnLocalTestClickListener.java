package edu.buffalo.cse.cse486586.simpledht;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;

public class OnLocalTestClickListener implements OnClickListener {

	private static final String TAG = OnLocalTestClickListener.class.getName();
	private static final int TEST_CNT = 50;
	private static final String KEY_FIELD = "key";
	private static final String VALUE_FIELD = "value";

	private final TextView mTextView;
	private final ContentResolver mContentResolver;
	private final Uri mUri;
	private final ContentValues[] mContentValues;

	public OnLocalTestClickListener(TextView _tv, ContentResolver _cr) {
		mTextView = _tv;
		mContentResolver = _cr;
		mUri = buildUri("content",
				"edu.buffalo.cse.cse486586.simpledht.provider");
		mContentValues = initTestValues();
	}

	private Uri buildUri(String scheme, String authority) {
		Uri.Builder uriBuilder = new Uri.Builder();
		uriBuilder.authority(authority);
		uriBuilder.scheme(scheme);
		return uriBuilder.build();
	}

	private ContentValues[] initTestValues() {
		ContentValues[] cv = new ContentValues[TEST_CNT];
		for (int i = 0; i < TEST_CNT; i++) {
			cv[i] = new ContentValues();
			cv[i].put(KEY_FIELD, "key" + Integer.toString(i));
			cv[i].put(VALUE_FIELD, "val" + Integer.toString(i));
		}

		return cv;
	}

	private class Task extends AsyncTask<Void, String, Void> {

		String result;

		@Override
		protected Void doInBackground(Void... params) {
			if (testInsert()) {
				publishProgress("Insert success\n");
			} else {
				publishProgress("Insert fail\n");
				return null;
			}

			if (testQuery()) {
				publishProgress("Query success\n" + result);
			} else {
				publishProgress("Query fail\n");
			}

			return null;
		}

		protected void onProgressUpdate(String... strings) {
			mTextView.append(strings[0]);

			return;
		}

		private boolean testInsert() {
			try {
				for (int i = 0; i < TEST_CNT; i++) {
					mContentResolver.insert(mUri, mContentValues[i]);
				}
			} catch (Exception e) {
				Log.e(TAG, e.toString());
				return false;
			}

			return true;
		}

		private boolean testQuery() {
			try {
				result = null;
				Cursor resultCursor = mContentResolver.query(mUri, null, "@",
						null, null);
				if (resultCursor == null) {
					Log.e(TAG, "Result null");
					throw new Exception();
				}
				while (resultCursor.moveToNext()) {
					int keyIndex = resultCursor.getColumnIndex(KEY_FIELD);
					int valueIndex = resultCursor.getColumnIndex(VALUE_FIELD);

					String returnKey = resultCursor.getString(keyIndex);
					String returnValue = resultCursor.getString(valueIndex);
					result += returnValue + "\n";
				}
			} catch (Exception e) {
				return false;
			}

			return true;
		}
	}

	@Override
	public void onClick(View v) {
		new Task().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
	}
}
