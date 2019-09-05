package abr.teleop;

import ioio.lib.api.AnalogInput;
import ioio.lib.api.DigitalOutput;
import ioio.lib.api.PwmOutput;
import ioio.lib.api.exception.ConnectionLostException;
import ioio.lib.util.BaseIOIOLooper;
import ioio.lib.util.IOIOLooper;
import ioio.lib.util.android.IOIOActivity;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.nio.*;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.GeomagneticField;
import android.location.Criteria;
import android.location.Location;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.view.Display;
import android.view.OrientationEventListener;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
//
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

public class IOIO extends IOIOActivity implements Callback, SensorEventListener, ConnectionCallbacks, OnConnectionFailedListener, PreviewCallback{
	private static final String TAG_IOIO = "CameraRobot-IOIO";
	private static final String TAG_CAMERA = "CameraRobot-Camera";

	Date date = new Date();

	static final int DEFAULT_PWM = 1500, MAX_PWM = 2000, MIN_PWM = 1000, PWM_STEP=10, K1 = 3, K2=1, K3=10;

	RelativeLayout layoutPreview;
	TextView txtspeed_motor, txtIP, txtgps, txtbhn, txtdest, txtsonars, txtrwd;

	int speed_motor = 0;
	int pwm_pan, pwm_tilt;
	int pwm_speed, pwm_steering;
	int gps_pwm_speed, gps_pwm_steering;
	
	Camera mCamera;
	Camera.Parameters params;
    SurfaceView mPreview;
    int startTime = 0;

    boolean write = false;
    
	IOIOService ioio;
	OutputStream out;
	DataOutputStream dos;
//	BufferedOutputStream dos;
    OrientationEventListener oel;
    OrientationManager om;
	
	int size, quality;
	String pass;
	boolean connect_state = false;
	static int steps_done = 0;
	static int move = 0;  //0: not move; 1: to move; 2: moving; 3: moved (send observation)
	static int move_turn = 1500;
	static int move_speed = 1500;
	static long move_start_time;
	static int move_period = 1000;


    int w, h;
    int[] rgbs;
    boolean initialed = false;

    boolean auto_navigation = true; //true: roadfollowing, false: shortcut mode
    boolean taskstate = false;

	static int sonar1_reading = 0;
	static int sonar2_reading = 0;
	static int sonar3_reading = 0;
	int actual_speed, actual_steering;

	//location variables
	private GoogleApiClient mGoogleApiClient;
	private double curr_lat;
	private double curr_lon;
	private double dest_lat;
	private double dest_lon;
	private Location curr_loc;
	private LocationRequest mLocationRequest;
	private LocationListener mLocationListener;
	Location dest_loc;
	float distance = 0;
	int locInx = 0;
	private Criteria criteria ;
	int wpmode = 0;
	ArrayList<Location> waypoints = new ArrayList<Location>();

	//gps movement variables
	int turn_left = 1425;
	int turn_right = 1575;
	int turn_none = 1500;
	int forward_fast = 1700;
	int forward_stop = 1500;
	int forward_slow = 1600;
	boolean reachedWayPt = false;
	boolean reachedLastWayPt = false;
	boolean skipWayPt = false;
	int reward = 0;
	long timeWayPtStart;
	Random randNumGen;
	double pRwd = 0.5;


	//sensors
	private SensorManager mSensorManager;
	private Sensor mCompass, mAccelerometer;
	float[] mGravity;
	float[] mGeomagnetic;
	public float heading = 0;
	public float bearing = 0;
    GeomagneticField geoField;


    // variables for writing out data
	File rrFile;
	File recordingFile;
	FileOutputStream fosRR;

	ToneGenerator toneG;
	float distanceThreshold = 20;
	double[] pWait;
	static final int T = 150;


	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN 
        		| WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE); 
		setContentView(R.layout.ioio);


		pass = getIntent().getExtras().getString("Pass");
		size = getIntent().getExtras().getInt("Size");
		quality = getIntent().getExtras().getInt("Quality");
		wpmode = Integer.parseInt(getIntent().getExtras().getString("Wpmode"));
		setWaypoints(waypoints, wpmode);
		randNumGen = new Random();
		txtdest = (TextView)findViewById(R.id.txtdest);
		if (waypoints.size() > 0) {
			dest_loc = waypoints.get(0);
			locInx = 0;
			txtdest.setText(locInx + ", " + dest_loc.getLatitude() + ", " + dest_loc.getLongitude());
			Log.i("waypoints size", ""+waypoints.size());
		} else {
			Log.e("Waypoints", "waypoints length is zero");
		}
		timeWayPtStart = System.currentTimeMillis();
		pWait = new double[T];
		setWaittime(pWait, pRwd);

		txtspeed_motor = (TextView)findViewById(R.id.txtSpeed);
		txtgps = (TextView)findViewById(R.id.txtgps);
		txtbhn = (TextView)findViewById(R.id.bhn);
		txtsonars = (TextView)findViewById(R.id.sonar);
		txtrwd = (TextView)findViewById(R.id.txtRwd);
		txtrwd.setText("pRwd: " + pRwd);

        
		txtIP = (TextView)findViewById(R.id.txtIP);
		txtIP.setText(getIP());

		mPreview = (SurfaceView)findViewById(R.id.preview);
        mPreview.getHolder().addCallback(this);
        mPreview.getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        
		ioio = new IOIOService(getApplicationContext(), mHandler, pass);
    	ioio.execute();

		layoutPreview = (RelativeLayout)findViewById(R.id.layoutPreview);
		layoutPreview.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				if(mCamera != null)
					mCamera.autoFocus(null);
			}
		});
		
    	om = new OrientationManager(this);


		// phone must be Android 2.3 or higher and have Google Play store
		// must have Google Play Services: https://developers.google.com/android/guides/setup

		mLocationListener = new LocationListener() {
			public void onLocationChanged(Location location) {
//				Context context = getApplicationContext();
//				CharSequence text;
				Toast toast;


				curr_loc = location;
				curr_lat = location.getLatitude();
				curr_lon = location.getLongitude();
				txtgps.setText(curr_loc.getLatitude() + "," + curr_loc.getLongitude());
				txtdest.setText(locInx + ", " + dest_loc.getLatitude() + ", " + dest_loc.getLongitude());


				if (taskstate) {
					int timeElapsed = (int) ((System.currentTimeMillis() - timeWayPtStart) / 1000);

					if (reachedLastWayPt) {
						taskstate = false;
						toneG = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
						toneG.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT, 3000);
						try {
							fosRR.close();
						} catch (IOException e) {
							Log.e("lineFollow", e.toString());
						}
					}
					else {
						if(curr_loc.distanceTo(dest_loc) < distanceThreshold) {
							auto_navigation = true;
							toneG = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
							toneG.startTone(ToneGenerator.TONE_CDMA_PIP, 3000);
							++locInx;
							if (locInx >= waypoints.size()) {
								reachedLastWayPt = true;
								reward = 10;
							} else {
								dest_loc = waypoints.get(locInx);
								timeWayPtStart = System.currentTimeMillis();
								skipWayPt = false;
								reachedWayPt = true;
								if (randNumGen.nextDouble() < pRwd) {
									reward = 1;
								} else {
									reward = 0;
								}
							}
							String mill_timestamp = System.currentTimeMillis() + "";
							String info = mill_timestamp + "," + curr_loc.getLatitude() + "," + curr_loc.getLongitude() + "," + locInx + "," + 1 + "\n";
							try {
								byte[] b = info.getBytes();
								fosRR.write(b);
							} catch (IOException e) {
								Log.e("lineFollow", e.toString());
							}
						} else {
							reachedWayPt = false;
							if (timeElapsed > 0 && !skipWayPt && locInx < (waypoints.size()-1) && randNumGen.nextDouble() > pWait[timeElapsed]) {
								auto_navigation = false;
								skipWayPt = true;
								locInx = randNumGen.nextInt(waypoints.size() - (locInx+1)) + (locInx + 1);
								toneG = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
								toneG.startTone(ToneGenerator.TONE_CDMA_ALERT_INCALL_LITE, 1000);
							}
							String mill_timestamp = System.currentTimeMillis() + "";
							String info = mill_timestamp + "," + curr_loc.getLatitude() + "," + curr_loc.getLongitude() + "," + locInx + ",-1\n";
							try {
								byte[] b = info.getBytes();
								fosRR.write(b);
								Log.i("test", "wrote");
							} catch (IOException e) {
								Log.e("lineFollow", e.toString());
							}
						}

					}

				}

				distance = location.distanceTo(dest_loc);
				bearing = location.bearingTo(dest_loc);
				if (bearing < 0) {
					bearing = bearing + 360;
				}
				txtbhn.setText(taskstate + ", " + auto_navigation + ", "+ bearing + ", " + heading + ", " + distance + ", " + distanceThreshold);
			}

			@SuppressWarnings("unused")
			public void onStatusChanged(String provider, int status, Bundle extras) {
			}

			@SuppressWarnings("unused")
			public void onProviderEnabled(String provider) {
			}

			@SuppressWarnings("unused")
			public void onProviderDisabled(String provider) {
			}
		};

		mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		mCompass = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
		mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

		buildGoogleApiClient();
		mLocationRequest = new LocationRequest();
		mLocationRequest.setInterval(100);
		mLocationRequest.setFastestInterval(20);
		mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

		try {
			Calendar calendar = Calendar.getInstance();
			java.util.Date now = calendar.getTime();
			java.sql.Timestamp currentTimestamp = new java.sql.Timestamp(now.getTime());
			String time = currentTimestamp.toString();
			time = time.replaceAll("[|?*<\":>+\\[\\]/']", "_");

			File[] externalDirs = getExternalFilesDirs(null);
			if(externalDirs.length > 1) {
				//rrFile = new File(externalDirs[1].getAbsolutePath() + "/rescuerobotics/"+time);
				rrFile = new File(Environment.getExternalStorageDirectory() + "/lineFollow/");
				if (!rrFile.exists()) {
					rrFile.mkdirs();
				}
			} else {
				//rrFile = new File(externalDirs[0].getAbsolutePath() + "/rescuerobotics/"+time);
				rrFile = new File(Environment.getExternalStorageDirectory() + "/lineFollow/");
				if (!rrFile.exists()) {
					rrFile.mkdirs();
				}
			}
			Log.d("lineFollow", "created directory " + Environment.getExternalStorageDirectory() + "/lineFollow/");
			recordingFile = new File(rrFile, time+".csv");
			recordingFile.createNewFile();

			fosRR = new FileOutputStream(recordingFile);
			String labels = "Time,Lat,Lon,Waypt,Reward\n";
			byte[] b = labels.getBytes();
			fosRR.write(b);
			Log.i("test","made folder");
		} catch (IOException e) {
			Log.e("lineFollow", e.toString());
		}


	}

	//Method necessary for google play location services
	protected synchronized void buildGoogleApiClient() {
		mGoogleApiClient = new GoogleApiClient.Builder(this)
				.addConnectionCallbacks(this)
				.addOnConnectionFailedListener(this)
				.addApi(LocationServices.API)
				.build();
	}

	@Override
	public void onConnected(Bundle connectionHint) {
		// Connected to Google Play services
		curr_loc = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
//		bearing = curr_loc.bearingTo(dest_loc);
//		if (bearing < 0) {
//			bearing = bearing + 360;
//		}
		txtgps.setText(curr_loc.getLatitude() + "," + curr_loc.getLongitude());
		startLocationUpdates();
	}

	//Method necessary for google play location services
	protected void startLocationUpdates() {
		LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, mLocationListener);
	}


	@Override
	public void onConnectionSuspended(int cause) {
		// The connection has been interrupted.
		// Disable any UI components that depend on Google APIs
		// until onConnected() is called.
	}

	//Method necessary for google play location services
	@Override
	public void onConnectionFailed(ConnectionResult result) {
		// This callback is important for handling errors that
		// may occur while attempting to connect with Google.
		//
		// More about this in the 'Handle Connection Failures' section.
		Log.i("gpsconnection","connection failed");
	}


	@Override
	public void onPause() {
		super.onPause();
		mSensorManager.unregisterListener(this);
		stopLocationUpdates();
		ioio.killTask();
		if (fosRR != null) {
			try {
				fosRR.close();
			} catch (IOException e){
				Log.e("record gps", e.toString());
			}
		}
		finish();
	}
	protected void stopLocationUpdates() {
		LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, mLocationListener);
	}


	@Override
	protected void onStart() {
		super.onStart();
		mGoogleApiClient.connect();
	}

	@Override
	protected void onStop() {
		super.onStop();
		mGoogleApiClient.disconnect();
	}
	protected void onDestroy(){
		super.onDestroy();
	}

	//Called whenever activity resumes from pause
	@Override
	public void onResume() {
		super.onResume();
		mSensorManager.registerListener(this, mCompass, SensorManager.SENSOR_DELAY_NORMAL);
		mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
		if (mGoogleApiClient.isConnected()) {
			startLocationUpdates();
		}
	}
	
	Handler mHandler = new Handler() {
		public void handleMessage(Message msg) {
			int command = msg.what;

			if(command == IOIOService.MESSAGE_PASS) {
				try {
					out = ((Socket)msg.obj).getOutputStream();
					dos = new DataOutputStream(out);
					connect_state = true;
//					Log.i(TAG_IOIO, "move == 3");
					move = 3;
					Log.i(TAG_IOIO, "Connect");
				} catch (IOException e) {
					Log.e(TAG_IOIO, e.toString());
				} 
			} else if(command == IOIOService.MESSAGE_WRONG) {
				ioio.killTask();
				new Handler().postDelayed(new Runnable() {
					public void run() {
						ioio = new IOIOService(getApplicationContext(), mHandler, pass);
						ioio.execute();
					}
				}, 1000);
			} else if(command == IOIOService.MESSAGE_DISCONNECTED) {
				Toast.makeText(getApplicationContext()
						, "Server down, willbe restart service in 1 seconds"
						, Toast.LENGTH_SHORT).show();
				ioio.killTask();
				new Handler().postDelayed(new Runnable() {
					public void run() {
						ioio = new IOIOService(getApplicationContext(), mHandler, pass);
						ioio.execute();
					}
				}, 1000);
			} else if(command == IOIOService.MESSAGE_CLOSE) {
				Log.e(TAG_IOIO, "Close");
				connect_state = false;
				ioio.killTask();
				new Handler().postDelayed(new Runnable() {
					public void run() {
						ioio = new IOIOService(getApplicationContext(), mHandler, pass);
						ioio.execute();
					}
				}, 1000);
			} else if(command == IOIOService.MESSAGE_NAVIGATEON) {
				auto_navigation = true;
				timeWayPtStart = System.currentTimeMillis();
				Toast.makeText(getApplicationContext()
						, "NAVIGATION IS ON!"
						, Toast.LENGTH_SHORT).show();

			} else if(command == IOIOService.MESSAGE_NAVIGATEOFF) {
				auto_navigation = false;
				Toast.makeText(getApplicationContext()
						, "NAVIGATION IS OFF!"
						, Toast.LENGTH_SHORT).show();
			} else if(command == IOIOService.MESSAGE_MOVE) {
				move_turn = msg.arg1;
				move = 1;
				Toast.makeText(getApplicationContext()
						, "MOVE "+ msg.arg1 + ", " + msg.arg2
						, Toast.LENGTH_SHORT).show();
			} else if(command == IOIOService.MESSAGE_RLTURN) {
				int step = msg.arg1;
				int turn = msg.arg2;
				// resend image
				if (step == -1) {
					move = 3;
				}
				if (step == steps_done) {
					move = 1;
					move_turn = turn;
					steps_done = steps_done + 1;
					Log.i("time check", "1, receive action " + new Date().getTime());
				}
				// txtspeed_motor.setText("speed_motor " +turn +", "+steps_done);
			} else if(command == IOIOService.MESSAGE_RLINIT) {
				move_speed = msg.arg1;
				steps_done = msg.arg2;
			} else if(command == IOIOService.MESSAGE_RLTILTPERIOD) {
				pwm_tilt = msg.arg1;
				move_period = msg.arg2;
			} else if(command == IOIOService.MESSAGE_STOP) {
				taskstate = false;
			} else if(command == IOIOService.MESSAGE_START) {
				taskstate = true;
			} else if(command == IOIOService.MESSAGE_WPPLUS) {
				locInx = (locInx + 1) % waypoints.size();
				dest_loc = waypoints.get(locInx);
				txtdest.setText(locInx + ", " + dest_loc.getLatitude() + ", " + dest_loc.getLongitude());
			} else if(command == IOIOService.MESSAGE_DISTHPLUS) {
				distanceThreshold += 1;
			} else if(command == IOIOService.MESSAGE_DISTHMINUS) {
				distanceThreshold -= 1;
			}
		}
	};


	
	public void onPreviewFrame(final byte[] arg0, Camera arg1) {
//		Log.i("previewframe", ""+new Date().getTime());
//		txtspeed_motor.setText("speed_motor " + String.valueOf(pwm_speed));
		// Log.i("Debug", Integer.toString(move_period));
		txtspeed_motor.setText(actual_speed + ", " + actual_steering);
		txtsonars.setText(sonar1_reading + ", " + sonar2_reading + ", " + sonar3_reading);
		if (!initialed) {
			w = mCamera.getParameters().getPreviewSize().width;
			h = mCamera.getParameters().getPreviewSize().height;
			rgbs = new int[w * h];
			initialed = true;
		}
		// Log.w("onPreviewFrame move", Integer.toString(move));
		if (arg0 != null && connect_state) {
			try {
				if (move == 3) {
					// Log.i("3", "send new observation");
					Log.i("time check", "0, send observation " + new Date().getTime());
					decodeYUV420(rgbs, arg0, w, h);
					ByteBuffer byteBuffer = ByteBuffer.allocate(rgbs.length * 4);
					IntBuffer intBuffer = byteBuffer.asIntBuffer();
					intBuffer.put(rgbs);
					byte[] array = byteBuffer.array();
					Log.i("send image ", Integer.toString(array.length));
					sendObs(array);
					move = 0;
				}
			} catch (OutOfMemoryError e) {
				Toast.makeText(getApplicationContext()
						, "Out of memory,  please decrease image quality"
						, Toast.LENGTH_SHORT).show();
				e.printStackTrace();
				finish();
			}
		}
	}
	
	public void decodeYUV420(int[] rgb, byte[] yuv420, int width, int height) {
    	final int frameSize = width * height;
    	
    	for (int j = 0, yp = 0; j < height; j++) {
    		int uvp = frameSize + (j >> 1) * width, u = 0, v = 0;
    		for (int i = 0; i < width; i++, yp++) {
    			int y = (0xff & ((int) yuv420[yp])) - 16;
    			if (y < 0) y = 0;
    			if ((i & 1) == 0) {
    				v = (0xff & yuv420[uvp++]) - 128;
    				u = (0xff & yuv420[uvp++]) - 128;
    			}
    			
    			int y1192 = 1192 * y;
    			int r = (y1192 + 1634 * v);
    			int g = (y1192 - 833 * v - 400 * u);
    			int b = (y1192 + 2066 * u);
    			
    			if (r < 0) r = 0; else if (r > 262143) r = 262143;
    			if (g < 0) g = 0; else if (g > 262143) g = 262143;
    			if (b < 0) b = 0; else if (b > 262143) b = 262143;
    			
    			rgb[yp] = 0xff000000 | ((r << 6) & 0xff0000) | ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);
    		}
    	}
    }

	public void sendObs(byte[] data) {
		try {
			// dos.writeInt(data.length);
			Log.i("image byte length", Integer.toString(data.length));
//			dos.writeInt(sonar1_reading);
//			dos.writeInt(sonar2_reading);
//			dos.writeInt(sonar3_reading);

			dos.write(data);
			out.flush();
		} catch (IOException e) {
			Log.e(TAG_IOIO, e.toString());
			connect_state = false;
		} catch (NullPointerException e) {
			Log.e(TAG_IOIO, e.toString());
		}
	}

	public void setWaypoints(ArrayList<Location> waypoints, int wpmode) {
		if (wpmode == 0) {
			Location l0 = new Location(""); l0.setLatitude(33.645569); l0.setLongitude(-117.842796); waypoints.add(l0);
        	Location l1 = new Location(""); l1.setLatitude(33.645256); l1.setLongitude(-117.842733); waypoints.add(l1);
        	Location l2 = new Location(""); l2.setLatitude(33.644977); l2.setLongitude(-117.842882); waypoints.add(l2);
        	Location l3 = new Location(""); l3.setLatitude(33.644825); l3.setLongitude(-117.843198); waypoints.add(l3);
        	Location l4 = new Location(""); l4.setLatitude(33.644887); l4.setLongitude(-117.843582); waypoints.add(l4);
        	Location l5 = new Location(""); l5.setLatitude(33.645194); l5.setLongitude(-117.843701); waypoints.add(l5);
        	Location l6 = new Location(""); l6.setLatitude(33.645617); l6.setLongitude(-117.843641); waypoints.add(l6);
        	Location l7 = new Location(""); l7.setLatitude(33.645778); l7.setLongitude(-117.843200); waypoints.add(l7);
		} else {
			Log.e("waypoints", "no waypoints");
		}
	}

	public void setWaittime(double[] pWait, double pRwd ) {
		if (pRwd < 0.51) {
			// Probability of waiting for a 50% reward to be delivered 40+/-20
			pWait[0] = 1;
			pWait[1] = 1;
			pWait[2] = 1;
			pWait[3] = 1;
			pWait[4] = 1;
			pWait[5] = 1;
			pWait[6] = 1;
			pWait[7] = 1;
			pWait[8] = 1;
			pWait[9] = 1;
			pWait[10] = 1;
			pWait[11] = 1;
			pWait[12] = 1;
			pWait[13] = 1;
			pWait[14] = 1;
			pWait[15] = 1;
			pWait[16] = 1;
			pWait[17] = 1;
			pWait[18] = 1;
			pWait[19] = 1;
			pWait[20] = 1;
			pWait[21] = 1;
			pWait[22] = 1;
			pWait[23] = 1;
			pWait[24] = 1;
			pWait[25] = 1;
			pWait[26] = 1;
			pWait[27] = 1;
			pWait[28] = 1;
			pWait[29] = 1;
			pWait[30] = 1;
			pWait[31] = 1;
			pWait[32] = 1;
			pWait[33] = 1;
			pWait[34] = 1;
			pWait[35] = 1;
			pWait[36] = 1;
			pWait[37] = 1;
			pWait[38] = 1;
			pWait[39] = 1;
			pWait[40] = 1;
			pWait[41] = 1;
			pWait[42] = 1;
			pWait[43] = 1;
			pWait[44] = 1;
			pWait[45] = 1;
			pWait[46] = 1;
			pWait[47] = 0.99999;
			pWait[48] = 0.99999;
			pWait[49] = 0.99998;
			pWait[50] = 0.99997;
			pWait[51] = 0.99995;
			pWait[52] = 0.99991;
			pWait[53] = 0.99985;
			pWait[54] = 0.99976;
			pWait[55] = 0.9996;
			pWait[56] = 0.99934;
			pWait[57] = 0.99891;
			pWait[58] = 0.99824;
			pWait[59] = 0.99718;
			pWait[60] = 0.99553;
			pWait[61] = 0.993;
			pWait[62] = 0.98919;
			pWait[63] = 0.98354;
			pWait[64] = 0.97533;
			pWait[65] = 0.96365;
			pWait[66] = 0.9474;
			pWait[67] = 0.92534;
			pWait[68] = 0.89616;
			pWait[69] = 0.85866;
			pWait[70] = 0.81196;
			pWait[71] = 0.75569;
			pWait[72] = 0.69032;
			pWait[73] = 0.6172;
			pWait[74] = 0.53869;
			pWait[75] = 0.45786;
			pWait[76] = 0.3782;
			pWait[77] = 0.30313;
			pWait[78] = 0.23549;
			pWait[79] = 0.17722;
			pWait[80] = 0.12918;
			pWait[81] = 0.09124;
			pWait[82] = 0.062495;
			pWait[83] = 0.041559;
			pWait[84] = 0.026869;
			pWait[85] = 0.016917;
			pWait[86] = 0.01039;
			pWait[87] = 0.0062369;
			pWait[88] = 0.0036656;
			pWait[89] = 0.0021134;
			pWait[90] = 0.0011974;
			pWait[91] = 0.00066781;
			pWait[92] = 0.00036726;
			pWait[93] = 0.00019945;
			pWait[94] = 0.00010712;
			pWait[95] = 5.6967e-05;
			pWait[96] = 3.0035e-05;
			pWait[97] = 1.5716e-05;
			pWait[98] = 8.1696e-06;
			pWait[99] = 4.2224e-06;
			pWait[100] = 2.1715e-06;
			pWait[101] = 1.112e-06;
			pWait[102] = 5.6733e-07;
			pWait[103] = 2.8854e-07;
			pWait[104] = 1.4635e-07;
			pWait[105] = 7.4058e-08;
			pWait[106] = 3.7403e-08;
			pWait[107] = 1.8859e-08;
			pWait[108] = 9.4955e-09;
			pWait[109] = 4.7753e-09;
			pWait[110] = 2.3992e-09;
			pWait[111] = 1.2044e-09;
			pWait[112] = 6.0415e-10;
			pWait[113] = 3.0289e-10;
			pWait[114] = 1.5178e-10;
			pWait[115] = 7.6027e-11;
			pWait[116] = 3.807e-11;
			pWait[117] = 1.9058e-11;
			pWait[118] = 9.5381e-12;
			pWait[119] = 4.7728e-12;
			pWait[120] = 2.388e-12;
			pWait[121] = 1.1946e-12;
			pWait[122] = 5.9754e-13;
			pWait[123] = 2.9887e-13;
			pWait[124] = 1.4948e-13;
			pWait[125] = 7.4754e-14;
			pWait[126] = 3.7383e-14;
			pWait[127] = 1.8694e-14;
			pWait[128] = 9.3481e-15;
			pWait[129] = 4.6744e-15;
			pWait[130] = 2.3374e-15;
			pWait[131] = 1.1688e-15;
			pWait[132] = 5.844e-16;
			pWait[133] = 2.9221e-16;
			pWait[134] = 1.4611e-16;
			pWait[135] = 7.3056e-17;
			pWait[136] = 3.6528e-17;
			pWait[137] = 1.8264e-17;
			pWait[138] = 9.1323e-18;
			pWait[139] = 4.5662e-18;
			pWait[140] = 2.2831e-18;
			pWait[141] = 1.1416e-18;
			pWait[142] = 5.7078e-19;
			pWait[143] = 2.8539e-19;
			pWait[144] = 1.427e-19;
			pWait[145] = 7.1348e-20;
			pWait[146] = 3.5674e-20;
			pWait[147] = 1.7837e-20;
			pWait[148] = 8.9185e-21;
			pWait[149] = 4.4593e-21;
		}
		else {
			// Probability of waiting for a 95% reward to be delivered 40+/-20
			pWait[0] = 1;
			pWait[1] = 1;
			pWait[2] = 1;
			pWait[3] = 1;
			pWait[4] = 1;
			pWait[5] = 1;
			pWait[6] = 1;
			pWait[7] = 1;
			pWait[8] = 1;
			pWait[9] = 1;
			pWait[10] = 1;
			pWait[11] = 1;
			pWait[12] = 1;
			pWait[13] = 1;
			pWait[14] = 1;
			pWait[15] = 1;
			pWait[16] = 1;
			pWait[17] = 1;
			pWait[18] = 1;
			pWait[19] = 1;
			pWait[20] = 1;
			pWait[21] = 1;
			pWait[22] = 1;
			pWait[23] = 1;
			pWait[24] = 1;
			pWait[25] = 1;
			pWait[26] = 1;
			pWait[27] = 1;
			pWait[28] = 1;
			pWait[29] = 1;
			pWait[30] = 1;
			pWait[31] = 1;
			pWait[32] = 1;
			pWait[33] = 1;
			pWait[34] = 1;
			pWait[35] = 1;
			pWait[36] = 1;
			pWait[37] = 1;
			pWait[38] = 1;
			pWait[39] = 1;
			pWait[40] = 1;
			pWait[41] = 1;
			pWait[42] = 1;
			pWait[43] = 1;
			pWait[44] = 1;
			pWait[45] = 1;
			pWait[46] = 1;
			pWait[47] = 1;
			pWait[48] = 1;
			pWait[49] = 1;
			pWait[50] = 1;
			pWait[51] = 1;
			pWait[52] = 1;
			pWait[53] = 1;
			pWait[54] = 1;
			pWait[55] = 1;
			pWait[56] = 1;
			pWait[57] = 1;
			pWait[58] = 1;
			pWait[59] = 1;
			pWait[60] = 1;
			pWait[61] = 1;
			pWait[62] = 1;
			pWait[63] = 1;
			pWait[64] = 1;
			pWait[65] = 1;
			pWait[66] = 1;
			pWait[67] = 1;
			pWait[68] = 1;
			pWait[69] = 1;
			pWait[70] = 1;
			pWait[71] = 1;
			pWait[72] = 1;
			pWait[73] = 1;
			pWait[74] = 1;
			pWait[75] = 1;
			pWait[76] = 1;
			pWait[77] = 1;
			pWait[78] = 1;
			pWait[79] = 1;
			pWait[80] = 1;
			pWait[81] = 1;
			pWait[82] = 0.99999;
			pWait[83] = 0.99996;
			pWait[84] = 0.99988;
			pWait[85] = 0.99966;
			pWait[86] = 0.99915;
			pWait[87] = 0.99797;
			pWait[88] = 0.99544;
			pWait[89] = 0.99038;
			pWait[90] = 0.98092;
			pWait[91] = 0.96441;
			pWait[92] = 0.93753;
			pWait[93] = 0.89673;
			pWait[94] = 0.83915;
			pWait[95] = 0.76371;
			pWait[96] = 0.67218;
			pWait[97] = 0.56939;
			pWait[98] = 0.46253;
			pWait[99] = 0.35955;
			pWait[100] = 0.2673;
			pWait[101] = 0.19018;
			pWait[102] = 0.12973;
			pWait[103] = 0.085071;
			pWait[104] = 0.053793;
			pWait[105] = 0.032913;
			pWait[106] = 0.019554;
			pWait[107] = 0.011319;
			pWait[108] = 0.0064044;
			pWait[109] = 0.0035531;
			pWait[110] = 0.001938;
			pWait[111] = 0.0010419;
			pWait[112] = 0.00055327;
			pWait[113] = 0.00029076;
			pWait[114] = 0.00015147;
			pWait[115] = 7.8334e-05;
			pWait[116] = 4.0264e-05;
			pWait[117] = 2.0592e-05;
			pWait[118] = 1.0487e-05;
			pWait[119] = 5.3222e-06;
			pWait[120] = 2.6934e-06;
			pWait[121] = 1.3599e-06;
			pWait[122] = 6.8533e-07;
			pWait[123] = 3.4484e-07;
			pWait[124] = 1.7329e-07;
			pWait[125] = 8.6998e-08;
			pWait[126] = 4.364e-08;
			pWait[127] = 2.1876e-08;
			pWait[128] = 1.096e-08;
			pWait[129] = 5.489e-09;
			pWait[130] = 2.748e-09;
			pWait[131] = 1.3754e-09;
			pWait[132] = 6.8823e-10;
			pWait[133] = 3.4433e-10;
			pWait[134] = 1.7225e-10;
			pWait[135] = 8.6156e-11;
			pWait[136] = 4.3091e-11;
			pWait[137] = 2.155e-11;
			pWait[138] = 1.0777e-11;
			pWait[139] = 5.3892e-12;
			pWait[140] = 2.6949e-12;
			pWait[141] = 1.3476e-12;
			pWait[142] = 6.7382e-13;
			pWait[143] = 3.3693e-13;
			pWait[144] = 1.6847e-13;
			pWait[145] = 8.4237e-14;
			pWait[146] = 4.2119e-14;
			pWait[147] = 2.106e-14;
			pWait[148] = 1.053e-14;
			pWait[149] = 5.2651e-15;
		}
	}

	public void switchSerotonin(View view) {
		if(pRwd == 0.5) {
			pRwd = 0.95;
		} else {
			pRwd = 0.5;
		}
		txtrwd.setText("pRwd: " + pRwd);
		setWaittime(pWait, pRwd);
	}

	@Override
	public final void onSensorChanged(SensorEvent event) {
		if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER)
			mGravity = event.values;
		if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD)
			mGeomagnetic = event.values;
		if (mGravity != null && mGeomagnetic != null) {
			float[] temp = new float[9];
			float[] R = new float[9];
			//Load rotation matrix into R
			SensorManager.getRotationMatrix(temp, null, mGravity, mGeomagnetic);
			//Remap to camera's point-of-view
			SensorManager.remapCoordinateSystem(temp, SensorManager.AXIS_X, SensorManager.AXIS_Z, R);
			//Return the orientation values
			float[] values = new float[3];
			SensorManager.getOrientation(R, values);
			//Convert to degrees
			for (int i = 0; i < values.length; i++) {
				Double degrees = (values[i] * 180) / Math.PI;
				values[i] = degrees.floatValue();
			}
			//Update the compass direction
			heading = values[0] + 11.665f; // from geoField.getDeclination() in Irvine
//			heading = values[0] + 11.435f; // from geoField.getDeclination() in Cardiff
			if (heading < 0) {
				heading += 360.0;
			}

			txtbhn.setText(taskstate + ", " + auto_navigation + ", "+bearing + ", " + heading + ", " + distance + ", " + distanceThreshold);

//            heading = (heading * 5 + fixWraparound(values[0] + 12)) / 6; //add 12 to make up for declination in Irvine, average out from previous 2 for smoothness
		}
	}


	public void surfaceChanged(SurfaceHolder arg0, int arg1, int arg2, int arg3) {
		if (mPreview == null)
			return;

		try {
			mCamera.stopPreview();
		} catch (Exception e){ }

		params = mCamera.getParameters();
		Camera.Size pictureSize = getMaxPictureSize(params);
		Camera.Size previewSize = params.getSupportedPreviewSizes().get(size);

		params.setPictureSize(pictureSize.width, pictureSize.height);
		params.setPreviewSize(previewSize.width, previewSize.height);
		params.setPreviewFrameRate(getMaxPreviewFps(params));

		Display display = getWindowManager().getDefaultDisplay();
		LayoutParams lp = layoutPreview.getLayoutParams();

		if(om.getOrientation() == OrientationManager.LANDSCAPE_NORMAL
				|| om.getOrientation() == OrientationManager.LANDSCAPE_REVERSE) {
			float ratio = (float)previewSize.width / (float)previewSize.height;
			if((int)((float)mPreview.getWidth() / ratio) >= display.getHeight()) {
				lp.height = (int)((float)mPreview.getWidth() / ratio);
				lp.width = mPreview.getWidth();
			} else {
				lp.height = mPreview.getHeight();
				lp.width = (int)((float)mPreview.getHeight() * ratio);
			}
		} else if(om.getOrientation() == OrientationManager.PORTRAIT_NORMAL
				|| om.getOrientation() == OrientationManager.PORTRAIT_REVERSE) {
			float ratio = (float)previewSize.height / (float)previewSize.width;
			if((int)((float)mPreview.getWidth() / ratio) >= display.getHeight()) {
				lp.height = (int)((float)mPreview.getWidth() / ratio);
				lp.width = mPreview.getWidth();
			} else {
				lp.height = mPreview.getHeight();
				lp.width = (int)((float)mPreview.getHeight() * ratio);
			}
		}

		layoutPreview.setLayoutParams(lp);
		int deslocationX = (int) (lp.width / 2.0 - mPreview.getWidth() / 2.0);
		layoutPreview.animate().translationX(-deslocationX);

		params.setJpegQuality(100);
		mCamera.setParameters(params);
		mCamera.setPreviewCallback(this);

		switch(om.getOrientation()) {
			case OrientationManager.LANDSCAPE_NORMAL:
				mCamera.setDisplayOrientation(0);
				break;
			case OrientationManager.PORTRAIT_NORMAL:
				mCamera.setDisplayOrientation(90);
				break;
			case OrientationManager.LANDSCAPE_REVERSE:
				mCamera.setDisplayOrientation(180);
				break;
			case OrientationManager.PORTRAIT_REVERSE:
				mCamera.setDisplayOrientation(270);
				break;
		}

		try {
			mCamera.setPreviewDisplay(mPreview.getHolder());
			mCamera.startPreview();
		} catch (Exception e){
			e.printStackTrace();
		}
	}

	public void surfaceCreated(SurfaceHolder arg0) {
		try {
			mCamera = Camera.open(0);
			mCamera.setPreviewDisplay(arg0);
			mCamera.startPreview();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void surfaceDestroyed(SurfaceHolder arg0) {
		mCamera.setPreviewCallback(null);
		mCamera.stopPreview();
		mCamera.release();
		mCamera = null;
	}

	@Override
	public final void onAccuracyChanged(Sensor sensor, int accuracy) {
		// Do something here if sensor accuracy changes.
	}
	
	public String getIP() {
		WifiManager wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        Method[] wmMethods = wifi.getClass().getDeclaredMethods();
        for(Method method: wmMethods){
	        if(method.getName().equals("isWifiApEnabled")) {

		        try {
		        	if(method.invoke(wifi).toString().equals("false")) {
		        		WifiInfo wifiInfo = wifi.getConnectionInfo();
		            	int ipAddress = wifiInfo.getIpAddress();
		            	String ip = (ipAddress & 0xFF) + "." +
		            			((ipAddress >> 8 ) & 0xFF) + "." +
		            			((ipAddress >> 16 ) & 0xFF) + "." +
		                        ((ipAddress >> 24 ) & 0xFF ) ;
		            	return ip;
				    } else if(method.invoke(wifi).toString().equals("true")) {
				    	return "192.168.43.1";
		          }
		        } catch (IllegalArgumentException e) {
		        } catch (IllegalAccessException e) {
		        } catch (InvocationTargetException e) {
		        }
	        }
        }
		return "Unknown";
	}
	
	public Camera.Size getMaxPictureSize(Camera.Parameters params) {
    	List<Camera.Size> pictureSize = params.getSupportedPictureSizes();
    	int firstPictureWidth, lastPictureWidth;
    	try {
	    	firstPictureWidth = pictureSize.get(0).width;
	    	lastPictureWidth = pictureSize.get(pictureSize.size() - 1).width;
	    	if(firstPictureWidth > lastPictureWidth) 
	    		return pictureSize.get(0);
	    	else 
	    		return pictureSize.get(pictureSize.size() - 1);
    	} catch (ArrayIndexOutOfBoundsException e) {
    		e.printStackTrace();
    		return pictureSize.get(0);
    	}
    }
    
    public int getMaxPreviewFps(Camera.Parameters params) {
    	List<Integer> previewFps = params.getSupportedPreviewFrameRates();
    	int fps = 0;
    	for(int i = 0 ; i < previewFps.size() ; i++) {
    		if(previewFps.get(i) > fps) 
    			fps = previewFps.get(i);
    	}
		Log.i("max fps", Integer.toString(fps));
		return fps;
    }

    public void switchTaskState(View view) {
		taskstate = !taskstate;
	}

	class Looper extends BaseIOIOLooper 
	{
		PwmOutput speed, steering, pan, tilt;
//		PulseInput sonar1,sonar2,sonar3;
		AnalogInput sonar1,sonar2,sonar3;
		DigitalOutput sonar_pulse;

		int sonarPulseCounter = 0;

//		int pwm_left_motor, pwm_right_motor;

    	
        protected void setup() throws ConnectionLostException 
        {
        	// control
        	pwm_speed = DEFAULT_PWM;
        	pwm_steering = DEFAULT_PWM;
        	pwm_pan = DEFAULT_PWM;
        	pwm_tilt = 1500;

        	speed = ioio_.openPwmOutput(3, 50);        	
        	steering = ioio_.openPwmOutput(4, 50);        	
        	pan = ioio_.openPwmOutput(5, 50);        	
        	tilt = ioio_.openPwmOutput(6, 50);

			speed.setPulseWidth(pwm_speed);
			steering.setPulseWidth(pwm_steering);
			pan.setPulseWidth(pwm_pan);
			tilt.setPulseWidth(pwm_tilt);

			// sonar
			sonarPulseCounter = 0;
//			sonar1 = ioio_.openPulseInput(12, PulseInput.PulseMode.POSITIVE);
//			sonar2 = ioio_.openPulseInput(13, PulseInput.PulseMode.POSITIVE);
//			sonar3 = ioio_.openPulseInput(14, PulseInput.PulseMode.POSITIVE);
			sonar1 = ioio_.openAnalogInput(42);
			sonar2 = ioio_.openAnalogInput(43);
			sonar3 = ioio_.openAnalogInput(44);
			sonar_pulse = ioio_.openDigitalOutput(40,true);

        	runOnUiThread(new Runnable() {
				public void run() {
					Toast.makeText(getApplicationContext(), 
							"Connected!", Toast.LENGTH_SHORT).show();
				}		
			});
        }

        public void loop() throws ConnectionLostException, InterruptedException 
        {
        	// Log.i("ioioloop", "loop "+ new Date().getTime());
//
//			float reading = sonar1.getDuration();
//			sonar1_reading = (float) (reading*1000.0*1000.0/147.0);
//
//			reading = sonar2.getDuration();
//			sonar2_reading = (float) (reading*1000.0*1000.0/147.0);
//
//			reading = sonar3.getDuration();
//			sonar3_reading = (float) (reading*1000.0*1000.0/147.0);
			float reading1 = sonar1.getVoltage();
			sonar1_reading = (int)(reading1/(float)(3.3/512)); //Only works for sonar with 3.3V input

			reading1 = sonar2.getVoltage();
			sonar2_reading = (int)(reading1/(float)(3.3/512)); //Only works for sonar with 3.3V input

			reading1 = sonar3.getVoltage();
			sonar3_reading = (int)(reading1/(float)(3.3/512)); //Only works for sonar with 3.3V input

			// movement based on road following
			switch (move){
				case 0:  // not move (keep on moving to be continuous)
					pwm_speed = 1600;
					// Log.i("loop", "move is 0");
					break;
				case 1:  // to move
					pwm_speed = move_speed;
					pwm_steering = move_turn;
					date = new Date();
					move_start_time = date.getTime();
					Log.i("time check", "2, start to move " + new Date().getTime());
					move = 2;
					//Log.i("1", "start to move");
					//Log.i("Debug start time", ""+move_start_time);
					break;
				case 2:  // do move
					date = new Date();
					//Log.i("Debug now time", ""+date.getTime());
					if ((date.getTime() - move_start_time) > move_period) {
						move = 3;
						Log.i("time check", "3, stop moving " + new Date().getTime());
					}
					break;
				case 3:  //after moving, send observation while don't stop, wait for new action
					pwm_speed = 1600;
					break;
			}

			// movement based on gps
			gps_pwm_speed = forward_fast;
			if (reachedWayPt){
				gps_pwm_speed = forward_stop;
				gps_pwm_steering = turn_none;
			} else {
				if (Math.abs(bearing-heading) < 5 || Math.abs(bearing-heading) > 355) {
					gps_pwm_steering = turn_none;
				}
				else if (heading > bearing) {
					if ((heading - bearing) < 180.0) {
						gps_pwm_steering = turn_left;
					}
					else {
						gps_pwm_steering = turn_right;
					}
				}
				else { // heading < bearing
					if ((bearing - heading) < 180.0) {
						gps_pwm_steering = turn_right;
					}
					else {
						gps_pwm_steering = turn_left;
					}
				}

			}
        	
//        	Log.e("IOIO", "pwm_left_motor: " + pwm_left_motor + " pwm_right_motor: " + pwm_right_motor+ " pwm_pan: " + pwm_pan+ " pwm_tilt: " + pwm_tilt);
			// If middle sonar close, stop and wait
			if (sonar2_reading < 10){
				gps_pwm_speed = forward_stop;
				gps_pwm_steering = turn_none;
				pwm_speed = forward_stop;
				pwm_steering = turn_none;
			}
			// If left sonar close, turn right
			else if (sonar1_reading < 12){
				gps_pwm_speed = forward_slow;
				gps_pwm_steering = turn_right;
				pwm_speed = forward_slow;
				pwm_steering = turn_right;
			}
			// If right sonar close, turn left
			else if (sonar3_reading < 12){
				gps_pwm_speed = forward_slow;
				gps_pwm_steering = turn_left;
				pwm_speed = forward_slow;
				pwm_steering = turn_left;
			}

        	if (!taskstate) {
        		speed.setPulseWidth(1500);
        		steering.setPulseWidth(1500);
        		actual_speed = 1500;
        		actual_steering = 1500;

			} else if (auto_navigation) {
				speed.setPulseWidth(pwm_speed);
				steering.setPulseWidth(pwm_steering);
				actual_speed = pwm_speed;
				actual_steering = pwm_steering;

			} else {
				speed.setPulseWidth(gps_pwm_speed);
				steering.setPulseWidth(gps_pwm_steering);
				actual_speed = gps_pwm_speed;
				actual_steering = gps_pwm_steering;

			}

        	pan.setPulseWidth(pwm_pan);
        	tilt.setPulseWidth(pwm_tilt);

			// read sonar


			Thread.sleep(10);
        }
        
		public void disconnected() {
        	runOnUiThread(new Runnable() {
				public void run() {
					Toast.makeText(getApplicationContext(), 
							"Disonnected!", Toast.LENGTH_SHORT).show();
				}		
			});
		}

		public void incompatible() {
        	runOnUiThread(new Runnable() {
				public void run() {
					Toast.makeText(getApplicationContext(), 
							"Imcompatible firmware version", Toast.LENGTH_SHORT).show();
				}		
			});
		}
    }

    protected IOIOLooper createIOIOLooper() {
        return new Looper();
    }
}
