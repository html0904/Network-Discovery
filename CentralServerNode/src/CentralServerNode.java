import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CentralServerNode implements Runnable {

  @SuppressWarnings("unchecked")
  public static void main(String[] args)
  {
    DatagramSocket socket;

    // Find the server using UDP broadcast.
    try {
      //Open a random port to send the package.
      socket = new DatagramSocket();
      socket.setSoTimeout(300);
      socket.setBroadcast(true);

      byte[] sendData = "CS_DISCOVER_REQUEST".getBytes();

      // Broadcast the message to the broadcast IP.
      InetAddress broadcastIP = getBroadcastIP();

      // Send the broadcast package.
      SendMessage(socket, sendData, broadcastIP);

      System.out.println(CentralServerNode.class.getName() + ">> Request packet sent to: " + broadcastIP.getHostAddress());
      System.out.println(CentralServerNode.class.getName() + ">> Now waiting for a reply!");

      //Wait for a response
      byte[] recvBuf = new byte[15000];

      DatagramPacket receivePacket = new DatagramPacket(recvBuf, recvBuf.length);
      List<InetAddress> nodes = new ArrayList<InetAddress>();
      int numServers = 0;

      while(true)
      {
        try {
          socket.receive(receivePacket);

          //We have a response
          System.out.println(CentralServerNode.class.getName() + ">> Broadcast response from server: " + receivePacket.getAddress().getHostAddress());
      
          //Check if the message is correct
          String message = new String(receivePacket.getData()).trim();
          if (message.equals("CS_DISCOVER_RESPONSE")) {
            nodes.add(receivePacket.getAddress());
            numServers++;
          }
        } catch (SocketTimeoutException e) {
          System.out.println("Timeout reached! " + e);
          break;
        }
      }

/*
      System.out.println("Found nodes on the network:");
      for (InetAddress ip : nodes)
      {
        System.out.println(ip.getHostAddress());
      }

      // Obtain response times.
      for (InetAddress ip : nodes)
      {
        SendMessage(socket, "OBTAIN_RESPONSE_TIMES".getBytes(), ip);
      }*/

      // Add central server IP to the list.
      /*
      InetAddress myIP = InetAddress.getLocalHost();
      System.out.println("myip = " + myIP.getHostAddress());
      nodes.add(myIP);
      */

      System.out.println("Initiate servers to obtain response times.");
      // Initiate servers to obtain response times.
      // Send list of IPs to the broadcast IP so that each node can begin obtaining response times.
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ObjectOutputStream outputStream = new ObjectOutputStream(out);
      outputStream.writeObject(nodes);
      outputStream.close();
      byte[] listIPs = out.toByteArray();
      SendMessage(socket, listIPs, broadcastIP);

      // Create a list to hold a list of pairs of response times.
      List<List<Pair<InetAddress,Long>>> responseTimeList = new ArrayList<List<Pair<InetAddress,Long>>>();

      System.out.println("Waiting to receive response times form servers. (" + numServers + " servers)");
      // Receive response times from servers.
      for(int j=0; j<numServers; j++)
      {
        try {
          System.out.println("socket.receive");
          socket.receive(receivePacket);

          //We have a response
          //System.out.println(CentralServerNode.class.getName() + ">> Server node response: " + receivePacket.getAddress().getHostAddress());

          ObjectInputStream inputStream = new ObjectInputStream(new ByteArrayInputStream(receivePacket.getData()));
          List<Pair<InetAddress,Long>> responseTimes = new ArrayList<Pair<InetAddress,Long>>();
          responseTimes = (List<Pair<InetAddress,Long>>) inputStream.readObject();

          // Add the list of response times to the list of list of response times.
          responseTimeList.add(responseTimes);

          System.out.println("Obtained the response times from the server nodes:");

          System.out.println("Server Node: " + responseTimes.get(0).getL().getHostAddress());
          for (int i=1; i<responseTimes.size(); i++)
          {
            System.out.println("To server: " + responseTimes.get(i).getL().getHostAddress());
            System.out.println("Response Time: " + responseTimes.get(i).getR().toString());
          }

        } catch (SocketTimeoutException e) {
          System.out.println("Timeout reached! " + e);
          break;
        } catch (ClassNotFoundException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
      }
      
      //Close the port!
      System.out.println("Closing socket.");
      socket.close();

    } catch (SocketException e1) {
      System.out.println("Socket closed " + e1);
    } catch (IOException ex) {
      Logger.getLogger(CentralServerNode.class.getName()).log(Level.SEVERE, null, ex);
    }

    System.out.println("DONE");
  }

    public static InetAddress getBroadcastIP() {
      InetAddress found_bcast_address=null;
      System.setProperty("java.net.preferIPv4Stack", "true"); 
       try
       {
         Enumeration<NetworkInterface> niEnum = NetworkInterface.getNetworkInterfaces();
         while (niEnum.hasMoreElements())
         {
           NetworkInterface ni = niEnum.nextElement();
           if(!ni.isLoopback())
           {
               for (InterfaceAddress interfaceAddress : ni.getInterfaceAddresses())
               {
                 found_bcast_address = interfaceAddress.getBroadcast();
                 //found_bcast_address = found_bcast_address.substring(1);
               }
           }
         }
       }
       catch (SocketException e)
       {
         e.printStackTrace();
       }

       return found_bcast_address;
    }

  @Override
  public void run() {
    // TODO Auto-generated method stub
  }

  public static void SendMessage(DatagramSocket socket, byte[] sendData, InetAddress ip)
  {
    try {
      DatagramPacket dataPacket = new DatagramPacket(sendData, sendData.length, ip, 8888);
      socket.send(dataPacket);
    } catch (Exception e) {
    }
  }

}
