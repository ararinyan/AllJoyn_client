package com.example.client;

import java.lang.reflect.Method;

import org.alljoyn.bus.BusAttachment;
import org.alljoyn.bus.BusException;
import org.alljoyn.bus.BusListener;
import org.alljoyn.bus.Mutable;
import org.alljoyn.bus.ProxyBusObject;
import org.alljoyn.bus.SessionListener;
import org.alljoyn.bus.SessionOpts;
import org.alljoyn.bus.Status;

import android.app.ProgressDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelUuid;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;

import com.example.client.R;

import android.bluetooth.BluetoothAdapter.LeScanCallback;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;


public class MainActivity extends ActionBarActivity {

	static {
		System.loadLibrary("alljoyn_java");
	}
	
	BusHandler mBusHandler;
	private ArrayAdapter<String> mListViewArrayAdapter;
	private ListView mListView;
	private ProgressDialog mProgressDialog;
	
	private static final String TAG = "Client";
	private static final int START_CONNECT_PROGRESS = 1;
	private static final int STOP_PROGRESS= 2;
	private static final int MESSAGE_REPLY = 3;
	private static final int MESSAGE_PING = 4;
	private static final int START_SCAN_PROGRESS = 5;
	
	private Handler mHandler = new Handler(){
		@Override
		public void handleMessage(Message msg){
			switch (msg.what){
			case START_CONNECT_PROGRESS:
				mProgressDialog = ProgressDialog.show(MainActivity.this,"","接続中",true,true);
				break;
			case STOP_PROGRESS:
				mProgressDialog.dismiss();
				break;
			case MESSAGE_REPLY:
				String rep = (String)msg.obj;
				mListViewArrayAdapter.add(rep);
				break;
			case MESSAGE_PING:
				Message ms = mBusHandler.obtainMessage(BusHandler.PING, msg.obj);
				mBusHandler.sendMessage(ms);
				break;
			case START_SCAN_PROGRESS:
				mProgressDialog = ProgressDialog.show(MainActivity.this,"","SCANNING",true,true);				
				break;
			default:
				break;
			}
		}
	};
	
	
		
    @Override
    protected void onCreate(Bundle savedInstanceState) {
    	Log.d(TAG,"起動");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        mListViewArrayAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1);
        mListView = (ListView) findViewById(R.id.ListView);
        mListView.setAdapter(mListViewArrayAdapter);
        
        final BluetoothManager mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        final BluetoothAdapter mBluetoothAdapter = mBluetoothManager.getAdapter();
                        
        HandlerThread busThread = new HandlerThread("BusHandler");
        busThread.start();
        mBusHandler = new BusHandler(busThread.getLooper());
        
        //CONNECT
        mBusHandler.sendEmptyMessage(BusHandler.CONNECT);
        mHandler.sendEmptyMessage(START_CONNECT_PROGRESS);
                
        Button btn = (Button)findViewById(R.id.send);
        btn.setOnClickListener(new View.OnClickListener(){
        	@Override
        	public void onClick(View v){
        		Log.d(TAG,"クリック");
        		
        		//iBeaconスキャン
        		mBluetoothAdapter.startLeScan(mLeScanCallback);
        		mHandler.sendEmptyMessage(START_SCAN_PROGRESS);
        		
        		//5秒後にスキャン停止
        		mHandler.postDelayed(new Runnable(){
        			@Override
        			public void run(){
        				mBluetoothAdapter.stopLeScan(mLeScanCallback);;
        				mHandler.sendEmptyMessage(STOP_PROGRESS);
        			}
        		}, 5000);
        	}
        });
        
        
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    public void onDestroy(){
    	super.onDestroy();
    	mBusHandler.sendEmptyMessage(BusHandler.DISCONNECT);
    	Log.d(TAG,"ですとろーい");
    }
    
    class BusHandler extends Handler {
    	private static final String SERVICE_NAME = "org.alljoyn.bus.samples.simple";
    	private static final short CONTACT_PORT=42;
    	
    	private BusAttachment mBus;
    	private ProxyBusObject mProxyObj;
    	private SimpleInterface mClientInterface;
    	
    	private int mSessionId;
    	private boolean mIsInASession;
    	private boolean mIsConnected;
    	private boolean mIsStoppringDiscovery;
    	
    	public static final int CONNECT = 1;
    	public static final int JOIN_SESSION = 2;
    	public static final int DISCONNECT = 3;
    	public static final int PING = 4;
    	
    	   public BusHandler(Looper looper) {
    	      super(looper);
    	      
    	      mIsInASession = false;
    	      mIsConnected = false;
    	      mIsStoppringDiscovery = false;
    	   }

    	   @Override
    	   public void handleMessage(Message msg) {
    	      switch (msg.what) {
    	      case CONNECT:{
    	    	      	    	  
    	    	  org.alljoyn.bus.alljoyn.DaemonInit.PrepareDaemon(getApplicationContext());
      	    	  
    	    	  mBus = new BusAttachment(getPackageName(), BusAttachment.RemoteMessage.Receive);


    	    	  mBus.registerBusListener(new BusListener() {
    	    		  
    	    		  //つながったら呼ばれる
                      @Override
                      public void foundAdvertisedName(String name, short transport, String namePrefix) {
                      	Log.d(TAG,"foundadvertisedname呼ばれた");
                      	if(!mIsConnected) {
                      	    Message msg = obtainMessage(JOIN_SESSION);
                      	    msg.arg1 = transport;
                      	    msg.obj = name;
                      	    sendMessage(msg);
                      	}
                      }
                  });
    	    	  
    	    	  //ここでpermission怒られてるっぽい
    	    	  Status status = mBus.connect();
    	    	  Log.d(TAG,"connect: "+status);
    	    	  if(Status.OK != status){
    	    		  finish();
    	    		  return;
    	    	  }
    	    	  
    	    	  status = mBus.findAdvertisedName(SERVICE_NAME);
    	    	  Log.d(TAG,"findadvertisedname: "+status);
    	    	  if(Status.OK != status){
    	    		  finish();
    	    		  return;
    	    	  }
    	    	  
    	    	      	    	  
    	    	  break;
    	      }
    	      
    	      case JOIN_SESSION:{
    	    	  Log.d(TAG,"join_session呼ばれた");
    	    	  if(mIsStoppringDiscovery){
    	    		  Log.d(TAG,"stoppingdiscovery");
    	    		  break;
    	    	  }
    	    	  short contactPort = CONTACT_PORT;
    	    	  SessionOpts sessionOpts = new SessionOpts();
    	    	  sessionOpts.transports = (short)msg.arg1;
    	    	  sessionOpts.isMultipoint = true;
    	    	  Mutable.IntegerValue sessionId = new Mutable.IntegerValue();
    	    	  
    	    	  Status status = mBus.joinSession((String) msg.obj, contactPort, sessionId, sessionOpts, new SessionListener(){
    	    		 @Override
    	    		 public void sessionLost(int sessionId, int reason){
    	    			 mIsConnected = false;
    	    		 }
    	    	  });
    	    	  Log.d(TAG,"joinnsesson: "+status);
    	    	  
    	    	  if(status == Status.OK){
    	    		  mProxyObj = mBus.getProxyBusObject(SERVICE_NAME, "/Service", sessionId.value, new Class<?>[]{ SimpleInterface.class});
    	    		  mClientInterface = mProxyObj.getInterface(SimpleInterface.class);
    	    		  
    	    		  mSessionId = sessionId.value;
    	    		  mIsConnected = true;
    	    		  mHandler.sendEmptyMessage(STOP_PROGRESS);
    	    	  }
    	    	  break;
    	      }
    	      
    	      case DISCONNECT:{
    	    	  mIsStoppringDiscovery = true;
    	    	  if(mIsConnected){
    	    		  //Status status =  mBus.leaveSession(mSessionId);
    	    		  Log.d(TAG,"leavesession");
    	    	  }
    	    	  mBus.disconnect();
    	    	  getLooper().quit();
    	    	  break;
    	      }
    	      case PING:{
    	    	  try{
    	    		  if(mClientInterface != null){
    	    			  String reply = mClientInterface.Ping((String) msg.obj);
    	    			  mHandler.sendMessage(mHandler.obtainMessage(MESSAGE_REPLY,reply));
    	    		  }
    	    	  } catch(BusException ex){
    	    		  Log.d(TAG,"exception "+ex);
    	    	  }
    	    	  break;
    	      }
    	      default:
    	         break;
    	      }
    	   }
    }
private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
		
		@Override
		public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
			// TODO Auto-generated method stub
						
			if(scanRecord.length > 30)
		    {
		        //iBeacon の場合 6 byte 目から、 9 byte 目はこの値に固定されている。
		        if((scanRecord[5] == (byte)0x4c) && (scanRecord[6] == (byte)0x00) &&
		        (scanRecord[7] == (byte)0x02) && (scanRecord[8] == (byte)0x15))
		        {
		            String uuid = IntToHex2(scanRecord[9] & 0xff) 
		                        + IntToHex2(scanRecord[10] & 0xff)
		                        + IntToHex2(scanRecord[11] & 0xff)
		                        + IntToHex2(scanRecord[12] & 0xff)
		                        + "-"
		                        + IntToHex2(scanRecord[13] & 0xff)
		                        + IntToHex2(scanRecord[14] & 0xff)
		                        + "-"
		                        + IntToHex2(scanRecord[15] & 0xff)
		                        + IntToHex2(scanRecord[16] & 0xff)
		                        + "-"
		                        + IntToHex2(scanRecord[17] & 0xff)
		                        + IntToHex2(scanRecord[18] & 0xff)
		                        + "-"
		                        + IntToHex2(scanRecord[19] & 0xff)
		                        + IntToHex2(scanRecord[20] & 0xff)
		                        + IntToHex2(scanRecord[21] & 0xff)
		                        + IntToHex2(scanRecord[22] & 0xff)
		                        + IntToHex2(scanRecord[23] & 0xff)
		                        + IntToHex2(scanRecord[24] & 0xff);
		 
		            String major = IntToHex2(scanRecord[25] & 0xff) + IntToHex2(scanRecord[26] & 0xff);
		            String minor = IntToHex2(scanRecord[27] & 0xff) + IntToHex2(scanRecord[28] & 0xff);
		            String message = "UUID: " + uuid + "\n RSSI: " + String.valueOf(rssi);
		            Message msg = mBusHandler.obtainMessage(mBusHandler.PING,message);
		            mBusHandler.sendMessage(msg);
		        }
		    }
		    
		}
		
		private String IntToHex2(int i) {
			char hex_2[] = {Character.forDigit((i>>4) & 0x0f,16),Character.forDigit(i&0x0f, 16)};
		    String hex_2_str = new String(hex_2);
		    return hex_2_str.toUpperCase();
		}
		
    };
}
