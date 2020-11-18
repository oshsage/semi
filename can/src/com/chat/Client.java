package com.chat;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.Set;

import org.java_websocket.client.WebSocketClient;
import org.json.simple.JSONObject;

import com.msg.Msg;
import com.ws.WsClient;
import com.properties.My;

import gnu.io.CommPort;
import gnu.io.CommPortIdentifier;
import gnu.io.SerialPort;
import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;

public class Client implements SerialPortEventListener {
	// 멤버 변수
	int port;
	String address;
	String id;
	Socket socket;
	Sender sender;

	// Serial 변수
	private BufferedInputStream bin;
	private InputStream in;
	private OutputStream out;
	private SerialPort serialPort;
	private CommPortIdentifier portIdentifier;
	private CommPort commPort;
	private String rawCanID, rawTotal;

	// 웹소켓
	static WebSocketClient WsClient;

	static My my = new My();

	// 기본생성자
	public Client() throws Exception {
	}
	
	// IP주소, 포트, ID를 담은 클라이언트 생성자
	public Client(String address, int port, String id) throws Exception {
		this.address = address;
		this.port = port;
		this.id = id;

		// WebSocket Client 선언, 최초 연결
		WsClient = new WsClient(new URI("ws://" + my.getWebsocketIp() + ":" + my.getWebsocektPort() + "/chatting"));
		WsClient.connect();

		// Serial 연결
		portIdentifier = CommPortIdentifier.getPortIdentifier(my.getSerialPort());
		System.out.printf("Port Connect : %s\n", my.getSerialPort());
		connectSerial();

	}

	public void connect() throws IOException {
		try {
			socket = new Socket(address, port);
		} catch (Exception e) {
			while (true) {
				try {
					Thread.sleep(2000);
					socket = new Socket(address, port);
					break;
				} catch (Exception e1) {
					System.out.println("Retry ...");
				}
			}
		}
		System.out.println("Connected Server: " + address);
		sender = new Sender(socket);
		new Receiver(socket).start();
	}

	public void sendTarget(String ip, String cmd) {
		// ArrayList<String> ips = new ArrayList<String>();
		// ips.add(ip);
		Msg msg = new Msg(id, cmd);
//		sender.setMsg(msg);
		new Thread(sender).start();
	}

	// 메세지 입력받음
	public void sendMsg(String ss) {
		Msg msg = new Msg(null, id, ss);
		sender.setMsg(msg);
		new Thread(sender).start();
		if (socket != null) {
			try {
				socket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		System.out.println("bye ...");
	}

	// 메세지 전송
	class Sender implements Runnable {
		Socket socket;
		ObjectOutputStream oo;
		Msg msg;

		public Sender(Socket socket) throws IOException {
			this.socket = socket;
			oo = new ObjectOutputStream(socket.getOutputStream());
		}

		public void setMsg(Msg msg) {
			this.msg = msg;
		}

		@Override
		public void run() {
			if (oo != null) {
				try {
					oo.writeObject(msg);
				} catch (IOException e) {
					// 서버가 죽어 있을 때
					// 더 이상의 메세지가 날라가지 않을 때 에러
					// e.printStackTrace();
					try {
						if (socket != null) {
							socket.close();
						}
					} catch (Exception e1) {
						e1.printStackTrace();
					}
					try {
						// 다시 서버와 연결 시도
						System.out.println("Retry ...");
						Thread.sleep(2000);
						connect();
					} catch (Exception e1) {
						e1.printStackTrace();
					}
				} // end try
			}
		}
	}

	class Receiver extends Thread {
		ObjectInputStream oi;

		public Receiver(Socket socket) throws IOException {
			oi = new ObjectInputStream(socket.getInputStream());
		}

		@Override
		public void run() {
			while (oi != null) {
				Msg msg = null;
				try {
					msg = (Msg) oi.readObject();
					if (msg.getMaps() != null) {
						HashMap<String, Msg> hm = msg.getMaps();
						Set<String> keys = hm.keySet();
						for (String k : keys) {
							System.out.println(k);
						}
						continue;
					}
					System.out.println(msg.getId() + msg.getMsg());

					// mobile client에서 보낸 메세지를 IoT Client로 전송
					sendIoT(msg.getMsg());
				} catch (Exception e) {
					// e.printStackTrace();
					break;
				}

			} // end while

			try {
				if (oi != null) {
					oi.close();
				}
				if (socket != null) {
					socket.close();
				}
			} catch (Exception e) {

			}
		}

	}

	// *********** Serial 통신 코드 ***********
	public void connectSerial() throws Exception {
		if (portIdentifier.isCurrentlyOwned()) {
			System.out.println("Error: Port is currently in use");
		} else {
			commPort = portIdentifier.open(this.getClass().getName(), 5000);
			if (commPort instanceof SerialPort) {
				serialPort = (SerialPort) commPort;
				serialPort.addEventListener(this);
				serialPort.notifyOnDataAvailable(true);
				serialPort.setSerialPortParams(9600, // 통신속도
						SerialPort.DATABITS_8, // 데이터 비트
						SerialPort.STOPBITS_1, // stop 비트
						SerialPort.PARITY_NONE); // 패리티
				in = serialPort.getInputStream();
				bin = new BufferedInputStream(in);
				out = serialPort.getOutputStream();
			} else {
				System.out.println("Error: Only serial ports are handled by this example.");
			}
		}
	}

	// Asynchronized Receive Data
	// --------------------------------------------------------

	public void serialEvent(SerialPortEvent event) {

		switch (event.getEventType()) {
		case SerialPortEvent.BI:
		case SerialPortEvent.OE:
		case SerialPortEvent.FE:
		case SerialPortEvent.PE:
		case SerialPortEvent.CD:
		case SerialPortEvent.CTS:
		case SerialPortEvent.DSR:
		case SerialPortEvent.RI:
		case SerialPortEvent.OUTPUT_BUFFER_EMPTY:
			break;
		case SerialPortEvent.DATA_AVAILABLE:
			byte[] readBuffer = new byte[128];

			try {
				while (bin.available() > 0) {
					int numBytes = bin.read(readBuffer);
				}

				String ss = new String(readBuffer);	// Data From Aruduino : "tmp26;hum80;"
				ss = ss.trim();
				System.out.println("Receive Raw Data:" + ss + "||");
				sendMsg(ss);		// Send raw to TCP/IP Server
				WsClient.send(ss);	// Send raw to DashBoard (Websocket)
				WsClient.send(convertJson(ss).toJSONString());	// Send JSON to DashBoard (Websocket)

			} catch (Exception e) {
				e.printStackTrace();
			}
			break;
		}
	}

	public void close() throws IOException {
		try {
			Thread.sleep(100);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		if (in != null) {
			in.close();
		}
		if (out != null) {
			out.close();
		}
		if (commPort != null) {
			commPort.close();
		}
	}

	public void sendIoT(String cmd) {
		Thread t1 = new Thread(new sendIoT(cmd));
		t1.start();
	}

	class sendIoT implements Runnable {
		String cmd;

		public sendIoT(String cmd) {
			this.cmd = cmd;
		}

		@Override
		public void run() {
			byte[] datas = cmd.getBytes();
			try {
				out.write(datas);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

	}
	

	public static void sendHttp(String data) {
		HttpSender sender = null;
		String urlstr = "http://" + my.getWebsocketIp() + ":" + my.getWebsocektPort() + "/chat";
		URL url = null;
		try {
			double temp = Double.parseDouble(data);
			url = new URL(urlstr + "?temp=" + temp);
			sender = new HttpSender(temp, url);
			new Thread(sender).start();
		} catch (Exception e) {
//			break;
		}
//		try {
//			Thread.sleep(2000);
//		} catch (InterruptedException e) {
//			e.printStackTrace();
//		}
	}

	// 아두이노에서 받은 센서데이터 > JSON 형식으로 변환 
	public JSONObject convertJson(String ss) {
		JSONObject jsonObj = new JSONObject();
		String[] dataArr = ss.split(";");
		
		for(int i=0; i<dataArr.length; i++) {
			switch(dataArr[i].substring(0,3)) {
				case "tmp":
					System.out.println("온도"+dataArr[i].substring(3));
					jsonObj.put("tmp", dataArr[i].substring(3));
					continue;
				case "hum":
					System.out.println("습도"+dataArr[i].substring(3));
					jsonObj.put("hum", dataArr[i].substring(3));
					continue;
			}	
		}
		return jsonObj;
	}

	static class HttpSender implements Runnable {

		URL url = null;
		double temp;

		public HttpSender() {
		}

		public HttpSender(double temp, URL url) {
			this.temp = temp;
			this.url = url;
		}

		@Override
		public void run() {
			HttpURLConnection con = null;
			try {
				con = (HttpURLConnection) url.openConnection();
				con.setReadTimeout(5000);
				con.setRequestMethod("POST");
				con.getInputStream();
				System.out.println("temp:" + temp);
			} catch (Exception e) {

			} finally {
				con.disconnect();
			}
		}

	}

	public static void main(String[] args) {
		try {
			Client client = new Client(my.getLocalIp(), my.getLocalPort(), "[IoTClient]");
			client.connect();
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		

	}

}