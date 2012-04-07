package com.simran.andltcminer;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Observable;
import java.util.Observer;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.StrictMode;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

//Merged with Miner.java :)

public class AndLTCMinerActivity extends Activity implements Observer {
	/** Called when the activity is first created. */

	private static final String TAG = "AndLTCMinerManagerActivity";
	private static final long DEFAULT_SCAN_TIME = 5000;
	private static final long DEFAULT_RETRY_PAUSE = 30000;

	public static final String PREFS_NAME = "prefs";
	SharedPreferences settings;

	private Worker worker;
	private long lastWorkTime;
	private long lastWorkHashes;

	private EditText URL, Cred;
	
	int temperature;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
		StrictMode.setThreadPolicy(policy);

		settings = getSharedPreferences(PREFS_NAME, 0);
		URL = (EditText)findViewById(R.id.editText1);
		Cred = (EditText)findViewById(R.id.editText2);
		
		this.registerReceiver(this.mBatInfoReceiver, 
		new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

		//Miner.main(new String[] { "--help" });


		Handler mHandler = new Handler();
		mHandler.postDelayed(runnable, 1);
	}

	void setThreads()
	{
		try
		{
			//log(Integer.toString(Runtime.getRuntime().availableProcessors()));
			Spinner threadList = (Spinner)findViewById(R.id.spinner1);

			String[] threadsAvailable = new String[Runtime.getRuntime().availableProcessors()];

			for(int i = 0; i <= Runtime.getRuntime().availableProcessors();i++)  
			{
				//log(Integer.toString(i));
				threadsAvailable[i] = Integer.toString(i + 1);
				ArrayAdapter threads = new ArrayAdapter<CharSequence>(this, android.R.layout.simple_spinner_item, threadsAvailable);
				threadList.setAdapter(threads);
			}
		}
		catch (Exception e){}
	}

	void setUI()
	{
		Button startMining = (Button)findViewById(R.id.button1);
		Button stopMining = (Button)findViewById(R.id.button2);

		//Not started
		stopMining.setEnabled(false);	

		URL.setText(settings.getString("URLText", "http://litecoinpool.org:9332/")); 
		Cred.setText(settings.getString("CredText", "user:password")); 

		if(worker != null) {
			if(worker.getStatus()) {
				startMining.setEnabled(false);
				stopMining.setEnabled(true);
			}
			else {
				startMining.setEnabled(true);
				stopMining.setEnabled(false);
			}
		}

		startMining.setOnClickListener(new View.OnClickListener() {         
			//@Override
			@Override
			public void onClick(View v) 
			{
				Spinner threadList = (Spinner)findViewById(R.id.spinner1);  				

				String URLText = URL.getText().toString();
				String CredText = Cred.getText().toString();

				SharedPreferences.Editor editor = settings.edit();
				editor.putString("URLText", URLText);
				editor.putString("CredText", CredText);
				editor.commit();

				startMiner(URLText, CredText, threadList.getSelectedItem().toString(), "1.0", "5000", "30000");
			}
		});

		stopMining.setOnClickListener(new View.OnClickListener() {         
			//@Override
			@Override
			public void onClick(View v) 
			{
				log("Stopping...");
				worker.stop();
			}
		});
	}

	void startMiner(String URL, String Auth, String Threads, String Throttle, String ScanTime, String RetryPause)
	{
		String[] args = { URL, Auth, Threads, Throttle, ScanTime, RetryPause };

		main(args);
	}


	private Runnable runnable = new Runnable() {
		@Override
		public void run() {
			setThreads();
			setUI();
			//startMiner("http://litecoinpool.org:9332/", "Simran.android:android", "2", "1.0", "5000", "30000");
		}
	};    



	void Miner(String url, String auth, long scanTime, long retryPause, int nThread, double throttle) {
		if (nThread < 1)
			throw new IllegalArgumentException("Invalid number of threads: " + nThread);
		if (throttle <= 0.0 || throttle > 1.0)
			throw new IllegalArgumentException("Invalid throttle: " + throttle);
		if (scanTime < 1L)
			throw new IllegalArgumentException("Invalid scan time: " + scanTime);
		if (retryPause < 0L)
			throw new IllegalArgumentException("Invalid retry pause: " + retryPause);
		try {
			worker = new Worker(new URL(url), auth, scanTime, retryPause, nThread, throttle);
		} catch (MalformedURLException e) {
			throw new IllegalArgumentException("Invalid URL: " + url);
		}
		worker.addObserver(this);
		Thread t = new Thread(worker);
		t.setPriority(Thread.MIN_PRIORITY);
		t.start();
		log(nThread + " miner threads started");
		setUI();
	}

	private static final DateFormat logDateFormat = new SimpleDateFormat("[yyyy-MM-dd HH:mm:ss] ");
	protected static final int REFRESH = 0;

	public void log(final String str) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {

				TextView Console = (TextView)findViewById(R.id.textView1);

				Console.append(logDateFormat.format(new Date()) + str + "\n");
				//Log.i(TAG, logDateFormat.format(new Date()) + str);

			}
		});
	}


	@Override
	public void update(Observable o, Object arg) {
		Worker.Notification n = (Worker.Notification) arg;
		if (n == Worker.Notification.SYSTEM_ERROR) {
			log("System error");
			System.exit(1);
		} else if (n == Worker.Notification.TERMINATED) {
			log("Miner shutdown");	
		} else if (n == Worker.Notification.PERMISSION_ERROR) {
			log("Permission error");
			System.exit(1);
		} else if (n == Worker.Notification.AUTHENTICATION_ERROR) {
			log("Invalid worker username or password");
			System.exit(1);
		} else if (n == Worker.Notification.CONNECTION_ERROR) {
			log("Connection error, retrying in " + worker.getRetryPause()/1000L + " seconds");
		} else if (n == Worker.Notification.COMMUNICATION_ERROR) {
			log("Communication error");
		} else if (n == Worker.Notification.LONG_POLLING_FAILED) {
			log("Long polling failed");
		} else if (n == Worker.Notification.LONG_POLLING_ENABLED) {
			log("Long polling activated");
		} else if (n == Worker.Notification.NEW_BLOCK_DETECTED) {
			log("LONGPOLL detected new block");
		} else if (n == Worker.Notification.POW_TRUE) {
			log("PROOF OF WORK RESULT: true (yay!!!)");
		} else if (n == Worker.Notification.POW_FALSE) {
			log("PROOF OF WORK RESULT: false (booooo)");
		} else if (n == Worker.Notification.NEW_WORK) {
			if (lastWorkTime > 0L) {
				long hashes = worker.getHashes() - lastWorkHashes;
				float speed = (float) hashes / Math.max(1, System.currentTimeMillis() - lastWorkTime);
				log(String.format("%d hashes, %.2f khash/s", hashes, speed) + " - " + temperature/10 + " C");
			}
			lastWorkTime = System.currentTimeMillis();
			lastWorkHashes = worker.getHashes();
		}
		hRefresh.sendEmptyMessage(REFRESH);
	}

	public void main(String[] args) {
		String url = null;
		String auth = null;
		int nThread = Runtime.getRuntime().availableProcessors();
		double throttle = 1.0;
		long scanTime = DEFAULT_SCAN_TIME;
		long retryPause = DEFAULT_RETRY_PAUSE;

		if (args.length > 0 && args[0].equals("--help")) {
			Log.i(TAG, "Usage:  java Miner [URL] [USERNAME:PASSWORD] [THREADS] [THROTTLE] [SCANTIME] [RETRYPAUSE]");
			return;
		}

		if (args.length > 0) url = args[0];
		if (args.length > 1) auth = args[1];
		if (args.length > 2) nThread = Integer.parseInt(args[2]);
		if (args.length > 3) throttle = Double.parseDouble(args[3]);
		if (args.length > 4) scanTime = Integer.parseInt(args[4]) * 1000L;
		if (args.length > 5) retryPause = Integer.parseInt(args[5]) * 1000L;

		try {
			Miner(url, auth, scanTime, retryPause, nThread, throttle);
		} catch (Exception e) {
			e.printStackTrace();
			//Log.e(TAG, e.getMessage());
		}
	}

	Handler hRefresh = new Handler(){
		@Override
		public void handleMessage(Message msg) {
			switch(msg.what){
			case REFRESH:
				/*Refresh UI*/
				setUI();
				break;
			}
		}
	};
	
	private BroadcastReceiver mBatInfoReceiver = new BroadcastReceiver(){
	    @Override
	    public void onReceive(Context arg0, Intent intent) {
	      temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
	    }
	  };
}