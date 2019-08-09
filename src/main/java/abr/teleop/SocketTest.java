package abr.teleop;


import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.io.File;
import java.io.IOException;

import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.CvType;

import android.graphics.Bitmap;


public class SocketTest {
    private final static Logger LOGGER =
            Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    private static Socket s;
    private static BufferedReader reader;


    public static void main(String[] args) {
        System.out.println(System.getProperty("java.library.path"));

        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
        System.out.println("start");
        Mat img = new Mat( 3, 3, CvType.CV_64FC1 );
        int row = 0, col = 0;
        img.put(row ,col, 0, -1, 0, -1, 5, -1, 0, -1, 0 );
        // TCP receive/send
        try {
            ServerSocket ss = new ServerSocket(21112);
            ss.setSoTimeout(2000);
            LOGGER.log(Level.INFO,  "waiting for connect");

            while (s == null) {  //connect
                try {
                    s = ss.accept();
                    s.setSoTimeout(2000);
                } catch (InterruptedIOException e) {
                    // LOGGER.log(Level.INFO, "Waiting for connect");
                } catch (SocketException e) {
                    LOGGER.log(Level.INFO, e.toString());
                }
            }
            LOGGER.log(Level.INFO, "connnected");
            //connected
            reader = new BufferedReader(new InputStreamReader(s.getInputStream()));
//            int i = 0;
//            while(true) {
//                System.out.println("i is :" + Integer.toString(i));
//                i = i + 1;
//                try {
//                    String str = reader.readLine();
//                    if (str.equals("q")){
//                        // s.close();
//                        LOGGER.log(Level.INFO, "now send");
//                        break;
//                    } else {
//                        System.out.println(str);
//                    }
//                } catch (SocketTimeoutException e) {
//                    // LOGGER.log(Level.INFO, e.toString());
//                }
//            }
            // read and send image
//            Mat img = new Mat( 3, 3, CvType.CV_64FC1 );
//            int row = 0, col = 0;
//            img.put(row ,col, 0, -1, 0, -1, 5, -1, 0, -1, 0 );
            
        } catch (IOException e) {
            LOGGER.log(Level.INFO, e.toString());
        }

        // UDP

    }
}