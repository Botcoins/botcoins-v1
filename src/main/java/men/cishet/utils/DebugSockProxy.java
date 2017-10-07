package men.cishet.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.Socket;

public class DebugSockProxy {
	public static void main(String[] args) {
		try {
			ServerSocket ss = new ServerSocket(5005);
			Tasks.runAsync(new ProxyListener(ss));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static class ProxyListener implements Runnable {

		private ServerSocket ss;

		public ProxyListener(ServerSocket ss) {
			this.ss = ss;
		}

		@Override
		public void run() {
			try (
					Socket upstream = ss.accept();
					Socket downstream = new Socket(new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("localhost", 1080)))
			) {
				Tasks.runAsync(new ProxyListener(ss));
				downstream.connect(new InetSocketAddress("localhost", 5005));
				InputStream upstreamIs = upstream.getInputStream();
				OutputStream upstreamOs = upstream.getOutputStream();

				InputStream downstreamIs = downstream.getInputStream();
				OutputStream downstreamOs = downstream.getOutputStream();

				Tasks.runAsync(() -> {
					byte[] req = new byte[1024];
					int b;
					try {
						while ((b = downstreamIs.read(req)) != -1) {
							upstreamOs.write(req, 0, b);
							upstreamOs.flush();
						}
					} catch (IOException e) {
						e.printStackTrace();
					} finally {
						try {
							upstream.close();
						} catch (IOException e) {
							e.printStackTrace();
						}
						try {
							downstream.close();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				});
				byte[] req = new byte[1024];
				int b;
				while ((b = upstreamIs.read(req)) != -1) {
					downstreamOs.write(req, 0, b);
					downstreamOs.flush();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}
