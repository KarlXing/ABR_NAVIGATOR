package abr.teleop;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;
import android.widget.ImageView;
import android.widget.Toast;

public class IOIOService extends AsyncTask<Void, Void, Void> {
	private static final String TAG = "CameraRobot-IOIOService";

	public static final int MESSAGE_UPDATE = 0;
	public static final int MESSAGE_CLOSE = 1;
	public static final int MESSAGE_TOAST = 2;
	public static final int MESSAGE_PASS = 3;
	public static final int MESSAGE_WRONG = 4;
	public static final int MESSAGE_DISCONNECTED = 5;

	public static final int MESSAGE_FLASH = 6;
	public static final int MESSAGE_SNAP = 7;
	public static final int MESSAGE_FOCUS = 8;

	public static final int MESSAGE_STOP = 10;
	public static final int MESSAGE_MOVE = 11;

	public static final int MESSAGE_PT_STOP = 19;
	public static final int MESSAGE_PT_MOVE = 20;

	public static final int MESSAGE_LOG = 21;
	public static final int MESSAGE_RLTURN = 22;
	public static final int MESSAGE_RLINIT = 23;
	public static final int MESSAGE_RLTILTPERIOD = 24;
	public static final int MESSAGE_NAVIGATEON = 25;
	public static final int MESSAGE_NAVIGATEOFF = 26;
	public static final int MESSAGE_DELAY = 27;
	public static final int MESSAGE_WPPLUS = 28;
	public static final int MESSAGE_START = 29;
	public static final int MESSAGE_DISTHPLUS = 30;
	public static final int MESSAGE_DISTHMINUS = 31;





	Boolean TASK_STATE = true;
	ServerSocket ss;
	ImageView mImageView;
	Context mContext;
	Bitmap bitmap;
	String mPassword;

	int count = 0;
	byte[] data;
	Socket s;
	BufferedWriter out;
	DataInputStream dis;
	InputStream in ;
	Handler mHandler;

	public IOIOService(Context context, Handler handler, String password) {
		mContext = context;
		mHandler = handler;
		mPassword = password;
	}

	byte[] buff;
	protected Void doInBackground(Void... params) {

		Runnable run = new Runnable() {
			public void run() {

				try {
					byte[] message = new byte[6];
					DatagramPacket p = new DatagramPacket(message, message.length);
					DatagramSocket s = new DatagramSocket(null);
					s.setReuseAddress(true);
					s.setBroadcast(true);
					s.bind(new InetSocketAddress(21111));

					while(TASK_STATE) {
						try {
							s.setSoTimeout(500);
							s.receive(p);
							String text = new String(message, 0, p.getLength());

							//							Log.e("IOIO", "msg received:" + text);

							if(text.substring(0, 2).equals("MC"))
							{
								int steering = Integer.parseInt(text.substring(2, 6));
								Log.i("text", text.substring(2,6)+steering);

								mHandler.obtainMessage(MESSAGE_MOVE, steering, steering).sendToTarget();

							} else if(text.equals("NAVION")) {
								mHandler.obtainMessage(MESSAGE_NAVIGATEON).sendToTarget();
							} else if(text.equals("NAVIOF")) {
								mHandler.obtainMessage(MESSAGE_NAVIGATEOFF).sendToTarget();
							} else if(text.substring(0,4).equals("STOP")) {
								mHandler.obtainMessage(MESSAGE_STOP).sendToTarget();
							} else if(text.substring(0,5).equals("START")) {
								mHandler.obtainMessage(MESSAGE_START).sendToTarget();
							} else if(text.equals("WPPLUS")) {
								mHandler.obtainMessage(MESSAGE_WPPLUS).sendToTarget();
							} else if(text.equals("DISTHP")) {
								mHandler.obtainMessage(MESSAGE_DISTHPLUS).sendToTarget();
							} else if(text.equals("DISTHM")) {
								mHandler.obtainMessage(MESSAGE_DISTHMINUS).sendToTarget();
							}
						} catch (SocketException e) {
							e.printStackTrace();
						} catch (SocketTimeoutException e) {
							//e.printStackTrace();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
					s.close();
					Log.e(TAG, "Kill Task");
				} catch (SocketException e) {
					e.printStackTrace();
				}
			}
		};
		new Thread(run).start();


		try {
			ss = new ServerSocket(21111);
			ss.setSoTimeout(2000);
			Log.w(TAG, "Waiting for connect");
			while(s == null && TASK_STATE) {
				try {
					s = ss.accept();
					s.setSoTimeout(1000);
				} catch (InterruptedIOException e) {
				} catch (SocketException e) {
					Log.w(TAG, e.toString());
				}
			}

			Log.i(TAG, "Waiting for password");
			if(TASK_STATE) {
				in = s.getInputStream();
				dis = new DataInputStream(in);
				buff = new byte[3];  //limit password to 3 bytes
				dis.readFully(buff);
				if (new String(buff).equals(mPassword)){
					Log.i(TAG,"pass match");
					mHandler.obtainMessage(MESSAGE_PASS, s).sendToTarget();

//					boolean delay = true;
//					try {
//						delay = s.getTcpNoDelay();
//					} catch (SocketException e) {
//						Log.i("socket delay", e.toString());
//					}
//					Log.i(TAG, "delay is"+delay);
                    s.setTcpNoDelay(true);

				} else {
					mHandler.obtainMessage(MESSAGE_WRONG, s).sendToTarget();
				}
			}
		} catch (IOException e) {
			Log.w(TAG, e.toString());
		}

		Log.i(TAG, "wait for set speed");
		try {
			int speed = dis.readInt();
			int steps_done = dis.readInt();
			Log.i("speed, steps_done", Integer.toString(speed) + Integer.toString(steps_done));
			mHandler.obtainMessage(MESSAGE_RLINIT, speed, steps_done).sendToTarget();
		} catch (IOException e) {
			Log.e(TAG, e.toString());
		}
		try {
			int tilt = dis.readInt();
			int period = dis.readInt();
			Log.i("tilt, period", Integer.toString(tilt) + Integer.toString(period));
			mHandler.obtainMessage(MESSAGE_RLTILTPERIOD, tilt, period).sendToTarget();
		} catch (IOException e) {
			Log.e(TAG, e.toString());
		}
		Log.i(TAG, "wait for start");


		while(TASK_STATE) {
			try {
				int step = dis.readInt();
				int turn = dis.readInt();
				Log.i("receive action", ""+step+","+turn);
				mHandler.obtainMessage(MESSAGE_RLTURN, step, turn).sendToTarget();
			} catch (EOFException e) {
				Log.w(TAG, e.toString());
				mHandler.obtainMessage(MESSAGE_CLOSE).sendToTarget();
				break;
			} catch (SocketTimeoutException e) { 
				// Log.w(TAG, e.toString());
			} catch (IOException e) { 
				Log.w(TAG, e.toString());
			} 

			if(!s.isConnected()) {
				Log.i(TAG, "Redisconnect");
				mHandler.obtainMessage(MESSAGE_DISCONNECTED).sendToTarget();
			}
		}
		try {

			ss.close();
			s.close();
			in.close();
			dis.close();
		} catch (IOException e) {
			Log.w(TAG, e.toString());
		} catch (NullPointerException e) {
			Log.w(TAG, e.toString());
		}
		Log.e(TAG, "Service was killed");
		return null;
	}

	public void killTask() {
		TASK_STATE = false;
	}
}
