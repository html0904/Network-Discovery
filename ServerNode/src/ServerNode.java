import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerNode implements Runnable
{
  DatagramSocket socket;

  public static void main(String [] args)
  {
    Thread serverNodeThread = new Thread(ServerNode.getInstance());
    serverNodeThread.start();

  }

  @SuppressWarnings("unchecked")
  @Override
  public void run()
  {
    try {
      // Keep a socket open to listen to all the UDP traffic that is destined for this port.
      socket = new DatagramSocket(8888, InetAddress.getByName("0.0.0.0"));
      socket.setBroadcast(true);
      InetAddress centralServer = null;
      int centralServerPort = 0;
      while (true) 
      {
        System.out.println(getClass().getName() + ">> Ready to receive broadcast packets!");

        // Receive a packet.
        byte[] recvBuf = new byte[15000];
        DatagramPacket packet = new DatagramPacket(recvBuf, recvBuf.length);
        socket.receive(packet);

        // Packet received.
        System.out.println(getClass().getName() + ">> Discovery packet received from: " 
                            + packet.getAddress().getHostAddress());
        //System.out.println(getClass().getName() + ">> Packet received; data: " 
        //                    + new String(packet.getData()));

        // See if the packet holds the right command (message).
        String message = new String(packet.getData()).trim();
        
        // Other servers are requesting for response times.
        if (message.equals("DISCOVER_REQUEST"))
        {
          byte[] sendData = "DISCOVER_RESPONSE".getBytes();

          //Send a response
          DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, packet.getAddress(), packet.getPort());
          socket.send(sendPacket);

          System.out.println(getClass().getName() + ">> Sent packet to: " + sendPacket.getAddress().getHostAddress());
        }
        // Central server requesting to discover all nodes on the network.
        else if (message.equals("CS_DISCOVER_REQUEST"))
        {
          System.out.println("Central Discover Request");

          // Save central server data
          centralServer = packet.getAddress();
          centralServerPort = packet.getPort();

          byte[] sendData = "CS_DISCOVER_RESPONSE".getBytes();

          //Send a response
          DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, packet.getAddress(), packet.getPort());
          socket.send(sendPacket);

          System.out.println(getClass().getName() + ">> Sent packet to: " + sendPacket.getAddress().getHostAddress());
        }
        // Obtain response times of every node.
        else
        {
          // Obtain the list of IP Addresses.
          ObjectInputStream inputStream = new ObjectInputStream(new ByteArrayInputStream(packet.getData()));
          List<InetAddress> nodes = new ArrayList<InetAddress>();
          nodes = (List<InetAddress>) inputStream.readObject();
        
          System.out.println("CS found nodes on the network:");
          for (InetAddress ip : nodes)
          {
            System.out.println(ip.getHostAddress());
          }

          // Get current IP address.
          InetAddress myIP = getFirstNonLoopbackAddress(true, false);

          // Create a list pair of response times.
          List<Pair<InetAddress,Long>> responseList = new ArrayList<Pair<InetAddress,Long>>();

          // Add own IP address first to indicate to CS which response time list this is coming from.
          Pair<InetAddress,Long> localIP = new Pair<InetAddress,Long>(myIP, (long) -1);
          responseList.add(localIP);

          System.out.println("Obtaining response times..");
          long startTime, endTime;

          // Ping every node and obtain response times.
          for (InetAddress ip : nodes)
          {
            System.out.println("ip server = " + ip.getHostAddress());
            System.out.println("local address = " + myIP.getHostAddress());

            // Do not ping own IP address.
            if (ip.getHostAddress().equals(myIP.getHostAddress())) {
              System.out.println("Ignore own IP address.");
              continue;
            }

            // Record time sent.
            startTime = System.currentTimeMillis();

            // Send message to server.
            SendMessage(socket, "DISCOVER_REQUEST".getBytes(), ip, 8888);

            // Receive reply from server.
            packet = new DatagramPacket(recvBuf, recvBuf.length);
            socket.receive(packet);

            // Print packet data received.
            System.out.println(getClass().getName() + ">>>> Discovery packet received from: "
                                                    + packet.getAddress().getHostAddress());
            System.out.println(getClass().getName() + ">>>> PACKET received; data: "
                                                    + new String(packet.getData()));

            // Record time received.
            endTime = System.currentTimeMillis();
          
            // Add response time to list.
            Pair<InetAddress,Long> responseTime = new Pair<InetAddress,Long>(ip, new  Long(endTime-startTime));
            responseList.add(responseTime);
          }

          System.out.println("Obtained the response times! (base: " + myIP.getHostAddress() + ")");
          for (Pair<InetAddress, Long> p : responseList)
          {
            System.out.println("IP: " + ((InetAddress) p.getL()).getHostAddress());
            System.out.println("Response Time: " + p.getR());
          }

          if (centralServer != null)
          {
            System.out.println("Central Server = " + centralServer.getHostAddress());

            // Send back results to central server.
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ObjectOutputStream outputStream = new ObjectOutputStream(out);
            outputStream.writeObject(responseList);
            outputStream.close();
            
            byte[] responseTimeList = out.toByteArray();

            System.out.println("Sending responseTimeList to central server.");
            SendMessage(socket, responseTimeList, centralServer, centralServerPort);
          }

        }
          
      }
    } catch (IOException ex) {
      Logger.getLogger(ServerNode.class.getName()).log(Level.SEVERE, null, ex);
    } catch (ClassNotFoundException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  public static ServerNode getInstance() {
    return ServerNodeThreadHolder.INSTANCE;
  }

  private static class ServerNodeThreadHolder {
    private static final ServerNode INSTANCE = new ServerNode();
  }
  
  public static void SendMessage(DatagramSocket socket, byte[] sendData, InetAddress ip, int port)
  {
    try {
      DatagramPacket dataPacket = new DatagramPacket(sendData, sendData.length, ip, port);
      socket.send(dataPacket);
    } catch (Exception e) {
      System.out.println("Woow");
    }
  }

  public static void ReceiveMessage(DatagramSocket socket, byte[] recvBuf)
  {
        DatagramPacket packet = new DatagramPacket(recvBuf, recvBuf.length);
        try {
      socket.receive(packet);
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  private static InetAddress getFirstNonLoopbackAddress(boolean preferIpv4, boolean preferIPv6) throws SocketException {
    Enumeration en = NetworkInterface.getNetworkInterfaces();
    while (en.hasMoreElements()) {
      NetworkInterface i = (NetworkInterface) en.nextElement();
      for (Enumeration en2 = i.getInetAddresses(); en2.hasMoreElements();) {
        InetAddress addr = (InetAddress) en2.nextElement();
        if (!addr.isLoopbackAddress()) {
          if (addr instanceof Inet4Address) {
            if (preferIPv6) {
              continue;
            }
            return addr;
          }
          if (addr instanceof Inet6Address) {
            if (preferIpv4) {
              continue;
            }
            return addr;
          }
        }
      }
    }
    return null;
  }

}