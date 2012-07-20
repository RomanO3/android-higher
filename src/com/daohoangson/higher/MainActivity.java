package com.daohoangson.higher;

import android.app.Activity;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.Button;

public class MainActivity extends Activity implements OnClickListener,
		SensorEventListener {
	final public static String TAG = "Higher/.MainActivity";
	final public static float EPSILON = 0.03f;
	final public static int REQUIRED_COUNTER = 3;
	final public static int MINIBAR_TO_CENTIMETRE = 900;
	final public static ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100);

	protected Button mBtnStart = null;
	protected WebView mWebviewDesc = null;

	protected SensorManager mSensorManager = null;
	protected Sensor mPressureSensor = null;

	protected Handler mHandler = new Handler();

	enum TrackingState {
		NOT_TRACKING, WAITING_FOR_FIRST, WAITING_FOR_SECOND
	}

	protected TrackingState mTrackingState;
	protected float mPressure1;
	protected float mPressure2;
	protected int mCounter;

	protected void setDescription(String description) {
		StringBuilder html = new StringBuilder(
				"<html><body><p align=\"justify\">");
		html.append(description);
		html.append("</p></body></html>");

		mWebviewDesc.loadData(html.toString(), "text/html", "utf-8");
		mWebviewDesc.setBackgroundColor(0x00000000);
	}

	protected void setDescription(int resourceId) {
		setDescription(getString(resourceId));
	}

	protected void doTrackingStart() {
		mSensorManager.registerListener(this, mPressureSensor,
				SensorManager.SENSOR_DELAY_NORMAL);
		Log.d(TAG, "Registered listener for presure sensor");

		// now waiting for the first pressure reading
		mTrackingState = TrackingState.WAITING_FOR_FIRST;
		Log.i(TAG, "Waiting for first presure reading...");

		mBtnStart.setEnabled(false);

		mPressure1 = 0.0f;
		mPressure2 = 0.0f;

		setDescription(R.string.put_down_until_beep);
	}

	protected void doWaitForSecond() {
		// now we have to wait for the second pressure reading
		mTrackingState = TrackingState.WAITING_FOR_SECOND;

		Log.d(TAG, String.format("First pressure reading is %1$f", mPressure1));
		Log.i(TAG, "Waiting for second presure reading...");

		setDescription(R.string.bring_up_until_beep);
		
		// play the tone
		tg.startTone(ToneGenerator.TONE_PROP_BEEP);
	}

	protected void doTrackingFinish() {
		// play the tone
		tg.startTone(ToneGenerator.TONE_PROP_BEEP);
		
		// unregister the listener asap
		mSensorManager.unregisterListener(this);
		Log.d(TAG, "Unregistered listener upon finished tracking");

		// reset the state and button for further runs
		mTrackingState = TrackingState.NOT_TRACKING;
		mBtnStart.setEnabled(true);

		int result = (int) (Math.abs(mPressure2 - mPressure1) * MINIBAR_TO_CENTIMETRE);
		setDescription(String.format(
				getString(R.string.result_x_click_start_again), result));

		Log.d(TAG, String.format("Second pressure reading is %1$f", mPressure2));
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		setContentView(R.layout.activity_main);

		mBtnStart = (Button) findViewById(R.id.btn_start);
		mBtnStart.setOnClickListener(this);

		mWebviewDesc = (WebView) findViewById(R.id.webview_desc);

		mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
		mPressureSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
	}

	protected void onResume() {
		super.onResume();

		if (mPressureSensor == null) {
			mBtnStart.setEnabled(false);
			setDescription(R.string.device_not_supported);
		} else {
			mBtnStart.setEnabled(true);
			setDescription(R.string.click_start_to_begin);
			mTrackingState = TrackingState.NOT_TRACKING;
		}
	}

	protected void onPause() {
		super.onPause();

		mSensorManager.unregisterListener(this);
		Log.d(TAG, "Unregistered listener upon Acitivity pause");
	}

	@Override
	public void onClick(View v) {
		if (v == mBtnStart) {
			if (mTrackingState != TrackingState.NOT_TRACKING) {
				Log.e(TAG, "mBtnStart has been clicked during tracking?!");
			} else if (mPressureSensor == null) {
				Log.e(TAG,
						"mBtnStart has been clicked with no pressure sensor found?!");
			} else {
				doTrackingStart();
			}
		}
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		Log.v(TAG, String.format("Accuracy changed for %1$s, new value: %2$d",
				sensor.getName(), accuracy));
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		float v = event.values[0];

		switch (mTrackingState) {
		case WAITING_FOR_FIRST:
			if (mPressure1 == 0.0f) {
				// this is the first value for it
				mPressure1 = v;
				mCounter = 0;

				Log.v(TAG, String.format("mPresure1 = %1$f", mPressure1));
			} else {
				float delta = Math.abs(v - mPressure1);
				mPressure1 = (v + mPressure1) / 2;
				
				if (delta < EPSILON) {
					if (mCounter < REQUIRED_COUNTER) {
						mCounter++;
						Log.v(TAG, String.format("mPresure1 = %2$f, counter = %1$d", mCounter, mPressure1));
					} else {
						// accept the value as it's good
						mHandler.postAtFrontOfQueue(new Runnable() {
							@Override
							public void run() {
								doWaitForSecond();
							}
						});
					}
				} else {
					mCounter = 0;
					Log.v(TAG, String.format("mPresure1 = %1$f, reset counter", mPressure1));
				}
			}
			break;
		case WAITING_FOR_SECOND:
			float deltaWithPressure1 = Math.abs(v - mPressure1);
			if (deltaWithPressure1 < EPSILON) {
				Log.v(TAG, String.format(
						"Value is too close to #1, ignored: %1$f vs. %2$f", v,
						mPressure1));
				mPressure2 = 0.0f;
				mCounter = 0;
			} else if (mPressure2 == 0.0f) {
				// this is the first value for it
				mPressure2 = v;
				mCounter = 0;
				Log.v(TAG, String.format("mPresure2 = %1$f", mPressure2));
			} else {
				float delta = Math.abs(v - mPressure2);
				mPressure2 = (v + mPressure2) / 2;
				
				if (delta < EPSILON) {
					if (mCounter < REQUIRED_COUNTER) {
						mCounter++;
						Log.v(TAG, String.format("mPresure2 = %2$f, counter = %1$d", mCounter, mPressure2));
					} else {
						// it's good now, accept it
						mHandler.postAtFrontOfQueue(new Runnable() {
							@Override
							public void run() {
								doTrackingFinish();
							}
						});
					}
				} else {
					mCounter = 0;
					Log.v(TAG, String.format("mPresure2 = %1$f, reset counter", mPressure2));
				}
			}
			break;
		default:
			Log.e(TAG, "Got sensor changed not during a tracking?!");
		}
	}
}
