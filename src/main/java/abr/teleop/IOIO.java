package abr.teleop;

import ioio.lib.api.DigitalOutput;
import ioio.lib.api.PulseInput;
import ioio.lib.api.PwmOutput;
import ioio.lib.api.exception.ConnectionLostException;
import ioio.lib.util.BaseIOIOLooper;
import ioio.lib.util.IOIOLooper;
import ioio.lib.util.android.IOIOActivity;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.nio.*;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.Bitmap.Config;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
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
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
//
//import com.google.android.gms.common.ConnectionResult;
//import com.google.android.gms.common.api.GoogleApiClient;
//import com.google.android.gms.location.LocationListener;
//import com.google.android.gms.location.LocationRequest;
//import com.google.android.gms.location.LocationServices;

public class IOIO extends IOIOActivity implements Callback, PreviewCallback{
	private static final String TAG_IOIO = "CameraRobot-IOIO";
	private static final String TAG_CAMERA = "CameraRobot-Camera";

	Date date = new Date();

	static final int DEFAULT_PWM = 1500, MAX_PWM = 2000, MIN_PWM = 1000, PWM_STEP=10, K1 = 3, K2=1, K3=10;

	RelativeLayout layoutPreview;
	TextView txtspeed_motor, txtIP;

	int speed_motor = 0;
	int pwm_pan, pwm_tilt;
	int pwm_speed, pwm_steering;
	
	Camera mCamera;
	Camera.Parameters params;
    SurfaceView mPreview;
    int startTime = 0;

    boolean write = false;
    
	IOIOService ioio;
	OutputStream out;
	DataOutputStream dos;
	
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

	static float sonar1_reading, sonar2_reading, sonar3_reading;

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

        txtspeed_motor = (TextView)findViewById(R.id.txtSpeed);
        
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

	}

	@Override
	protected void onStart() {
		super.onStart();
	}

	@Override
	protected void onStop() {
		super.onStop();
	}
	protected void onDestroy(){
		super.onDestroy();
	}

	//Called whenever activity resumes from pause
	@Override
	public void onResume() {
		super.onResume();
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
			}
			else if(command == IOIOService.MESSAGE_RLTURN) {
				int step = msg.arg1;
				int turn = msg.arg2;
				Log.i("execute action", ""+step+","+turn+","+steps_done);
				// resend image
				if (step == -1) {
					move = 3;
				}
				if (step == steps_done) {
					move = 1;
					move_turn = turn;
					steps_done = steps_done + 1;
				}
				txtspeed_motor.setText("speed_motor " + String.valueOf(pwm_speed));
			}
			else if(command == IOIOService.MESSAGE_RLINIT) {
				move_speed = msg.arg1;
				steps_done = msg.arg2;
			}
			else if(command == IOIOService.MESSAGE_RLTILTPERIOD) {
				pwm_tilt = msg.arg1;
				move_period = msg.arg2;
			}
		}
	};
	
	public void onPause() {
        super.onPause();
		ioio.killTask();
		finish();
    }


	
	public void onPreviewFrame(final byte[] arg0, Camera arg1) {
//		txtspeed_motor.setText("speed_motor " + String.valueOf(pwm_speed));
		Log.i("Debug", Integer.toString(move_period));
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
					Log.i("3", "send new observation");
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
			dos.writeFloat(sonar1_reading);
			dos.writeFloat(sonar2_reading);
			dos.writeFloat(sonar3_reading);

			dos.write(data);
			out.flush();
		} catch (IOException e) {
			Log.e(TAG_IOIO, e.toString());
			connect_state = false;
		} catch (NullPointerException e) { 
			Log.e(TAG_IOIO, e.toString());
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

	public void sendString(String str) {
		try {
			dos.writeInt(str.length());
			dos.write(str.getBytes());
			out.flush();
		} catch (IOException e) {
			Log.e(TAG_IOIO, e.toString());
			connect_state = false;
		} catch (NullPointerException e) { 
			Log.e(TAG_IOIO, e.toString());
		}
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
    	return fps;
    }

	class Looper extends BaseIOIOLooper 
	{
		PwmOutput speed, steering, pan, tilt;
		PulseInput sonar1,sonar2,sonar3;
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
			sonar1 = ioio_.openPulseInput(12, PulseInput.PulseMode.POSITIVE);
			sonar2 = ioio_.openPulseInput(13, PulseInput.PulseMode.POSITIVE);
			sonar3 = ioio_.openPulseInput(14, PulseInput.PulseMode.POSITIVE);
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
        	Log.i("Debug move", Integer.toString(move));
			switch (move){
				case 0:  // not move
					pwm_speed = 1500;
					pwm_steering = 1500;
					// Log.i("loop", "move is 0");
					break;
				case 1:  // to move
					pwm_speed = move_speed;
					pwm_steering = move_turn;
					date = new Date();
					move_start_time = date.getTime();
					move = 2;
					Log.i("1", "start to move");
					Log.i("Debug start time", ""+move_start_time);
					break;
				case 2:  // do move
					date = new Date();
					Log.i("Debug now time", ""+date.getTime());
					if ((date.getTime() - move_start_time) > move_period) {
						pwm_speed = 1500;
						pwm_steering = 1500;
						float reading = sonar1.getDuration();
						sonar1_reading = (float) (reading*1000.0*1000.0/147.0);

						reading = sonar2.getDuration();
						sonar2_reading = (float) (reading*1000.0*1000.0/147.0);

						reading = sonar3.getDuration();
						sonar3_reading = (float) (reading*1000.0*1000.0/147.0);
						move = 3;
						Log.i("2", "finish moving");
					}
					break;
				case 3:  //after moving, send observation
					pwm_speed = 1500;
					pwm_steering = 1500;
					break;
			}
        	
//        	Log.e("IOIO", "pwm_left_motor: " + pwm_left_motor + " pwm_right_motor: " + pwm_right_motor+ " pwm_pan: " + pwm_pan+ " pwm_tilt: " + pwm_tilt);
        	
        	speed.setPulseWidth(pwm_speed);
        	steering.setPulseWidth(pwm_steering);
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
