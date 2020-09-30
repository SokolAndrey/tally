package com.uber.m3.tally.m3;

import com.uber.m3.tally.Counter;
import com.uber.m3.tally.Timer;
import com.uber.m3.tally.RootScopeBuilder;
import com.uber.m3.tally.StatsReporter;
import com.uber.m3.tally.Scope;
import com.uber.m3.util.Duration;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SocketCloseTest {
    public static final int MAX_PACKET_SIZE = 1024;
    public static final int MAX_QUEUE_SIZE = 5;
    public static final int PORT = 9998;
    public static final SocketAddress SOCKET_ADDRESS = new InetSocketAddress("localhost", PORT);
    private DatagramSocket socket;

    @Before
    public void setup() {
        try {
            socket = new DatagramSocket(PORT);
        } catch(SocketException e) {
            e.printStackTrace();
        }
        new UDPServer(socket).start();
    }

    @Test
    public void main() {
        StatsReporter reporter = new M3Reporter.Builder(SOCKET_ADDRESS)
            .maxPacketSizeBytes(MAX_PACKET_SIZE)
            .maxQueueSize(MAX_QUEUE_SIZE)
            .service("test-service")
            .env("test")
            .build();

        Scope scope = new RootScopeBuilder()
            .reporter(reporter)
            .reportEvery(Duration.ofMillis(1000));

        Runnable emitter = new MetricsEmitter(scope);
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(emitter, 0, 200, TimeUnit.MILLISECONDS);

        try {
            Thread.sleep(10_000);

            System.out.println("closing socket");
            socket.close();
            System.out.println("socket closed!");

            Thread.sleep(120_000);
        } catch(InterruptedException e) {
            System.out.println("Interrupted");
        }
    }

    public class MetricsEmitter extends Thread {
        private Scope scope;

        MetricsEmitter(Scope scope) {
            this.scope = scope;
        }

        public void run() {
            String timerName = String.format("timer-%d", System.currentTimeMillis());
            Timer g = scope.timer(timerName);
            g.record(Duration.ofMillis(1234));
            System.out.println(String.format("Recorded %s", timerName));
        }
    }

    public class UDPServer extends Thread {
        private DatagramSocket socket;
        private byte[] buf = new byte[MAX_PACKET_SIZE];

        UDPServer(DatagramSocket socket) {
            this.socket = socket;
        }

        public void run() {
            while (true) {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                try {
                    socket.receive(packet);

//                    InetAddress address = packet.getAddress();
//                    int port = packet.getPort();
//                    packet = new DatagramPacket(buf, buf.length, address, port);
//                    String received = new String(packet.getData(), 0, packet.getLength());
//                    System.out.println(String.format("Received: %s", received));
                } catch(IOException e) {
                    break;
                }
            }
        }
    }
}