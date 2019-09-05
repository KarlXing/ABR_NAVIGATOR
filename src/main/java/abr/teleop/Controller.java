package abr.teleop;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.RelativeLayout;

public class Controller extends Activity{
	private final static String TAG = "CameraRobot-Controller";
	
    public static final int MESSAGE_DATA_RECEIVE = 0;
    
    Button buttonUp, buttonDown, buttonLeft, buttonRight;
    ImageView imageView1;
    CheckBox cbFlash;
	CheckBox cbLog;
    
    RelativeLayout layout_joystick, layout_joystick_PT;
	JoyStickClass js, js_PT;
    int screenWidth, screenHeight;
    
	Boolean task_state = true;
	
	OutputStream out; 
	DataOutputStream dos;
	InputStream in;
	DataInputStream dis;
	
	Socket s;
	String ip, pass;

	int pwm_speed = 1500;
	int pwm_steering = 1500;
    
	protected void onCreate(Bundle savedInstanceState) {
		
		super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN 
        		| WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE); 
		setContentView(R.layout.controller);
 
        Display display = getWindowManager().getDefaultDisplay(); 

        screenWidth = display.getWidth();
        screenHeight = display.getHeight();
        
		ip = getIntent().getExtras().getString("IP");
		pass = getIntent().getExtras().getString("Pass");

	}

//	public void doTurn(View view) {
//		int id = view.getId();
//		if (id == R.id.turn1){
//			send("MC"+1400);
//		} else if (id == R.id.turn2) {
//			send("MC" + 1425);
//		} else if (id == R.id.turn3) {
//			send("MC"+1450);
//		} else if (id == R.id.turn4) {
//			send("MC"+1500);
//		} else if (id == R.id.turn5) {
//			send("MC"+1550);
//		} else if (id == R.id.turn6) {
//			send("MC"+1575);
//		} else {
//			send("MC"+1600);
//		}
//	}

	public void switchNavigation(View view) {
		int id = view.getId();
		if (id == R.id.roadfollow) {
			send("NAVION");
		} else {
			send("NAVIOF");
		}
	}

	public void switchTaskstate(View view) {
		int id = view.getId();
		if (id == R.id.starttask){
			send("START!");
		} else {
			send("STOP!!");
		}
	}

	public void wpPlus(View view) {
		send("WPPLUS");
	}

	public void disthp(View view) {
		send("DISTHP");
	}

	public void disthm(View view) {
		send("DISTHM");
	}
	
	public void onPause() {
		super.onPause();

		task_state = false;
		
		finish();
	}

	public void start(View view) {
		sendInt(1);
	}

	public void cancel(View view) {
		sendInt(0);
	}

	public void sendInt(int num) {
		try {
			dos.writeInt(num);
			out.flush();
		} catch (IOException e) {
			Log.e(TAG, e.toString());
		} catch (NullPointerException e) {
			Log.e(TAG, e.toString());
		}
	}

	public void sendString(String str) {
		try {
			dos.writeInt(str.length());
			dos.write(str.getBytes());
			out.flush();
		} catch (IOException e) {
			Log.e(TAG, e.toString());
		} catch (NullPointerException e) {
			Log.e(TAG, e.toString());
		}
	}
	
	public void send(final String str) {
		new Thread(new Runnable() {
			public void run() {
				try {
//					Log.e("controller", "sending..." + str);
					
					DatagramSocket s = new DatagramSocket();
					InetAddress local = InetAddress.getByName(ip);
					DatagramPacket p = new DatagramPacket(str.getBytes(), str.getBytes().length, local, 21111);
					s.send(p);
					s.close();
				} catch (UnknownHostException e) {
					e.printStackTrace();
				} catch (SocketException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}).start();
		
	}
}
