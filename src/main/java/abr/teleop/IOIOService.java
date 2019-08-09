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

		try {
			ss = new ServerSocket(21111);
			ss.setSoTimeout(2000);
			Log.w(TAG, "Waiting for connect");
			while(s == null && TASK_STATE) {
				try {
					s = ss.accept();
					s.setSoTimeout(2000);
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
					mHandler.obtainMessage(MESSAGE_PASS, s).sendToTarget();
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
			Log.i("speed, steps_done", Integer.toString(tilt) + Integer.toString(period));
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
