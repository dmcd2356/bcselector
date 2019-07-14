/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package bcselector;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;
import util.Utils;

/**
 *
 * @author dan
 */
public class ServerInterface {
  
  private static final String NEWLINE = System.getProperty("line.separator");

  private static String    serverAddress = "localhost";
  private static int       serverPort = 6000;
  private static Socket    tcpDataSocket;
  private static BufferedOutputStream tcpDataOut;
  private static boolean   valid;
  
  public ServerInterface(int port) {
    serverPort = port;
    
    try {
      tcpDataSocket = new Socket(serverAddress, serverPort);
      tcpDataOut = new BufferedOutputStream(tcpDataSocket.getOutputStream());
      Utils.printStatusInfo("ServerInterface started on " + serverAddress + " port " + serverPort);
      valid = true;
    } catch (SecurityException | IOException ex) {
      Utils.printStatusError("ServerInterface: " + ex.getMessage());
      valid = false;
//      System.exit(1);
    }
  }
  
  public void exit() {
    try {
      if (tcpDataSocket != null) {
        tcpDataSocket.close();
      }
      if (tcpDataOut != null) {
        tcpDataOut.close();
      }
    } catch (IOException ex) {
      Utils.printStatusError("ServerInterface: " + ex.getMessage());
    }
  }
  
  public int getPort() {
    return serverPort;
  }
  
  public boolean isValid() {
    return valid;
  }
  
  public void sendMessage(String message) {
    if (!valid) {
      Utils.printStatusError("ServerInterface.sendMessage: port failure");
      return;
    }

    Utils.printStatusMessage(message);
    
    // send the message
    try {
      message += Utils.NEWLINE;
      tcpDataOut.write(message.getBytes());
      tcpDataOut.flush();
    } catch (IOException | NullPointerException ex) {
      Utils.printStatusError("ServerInterface.sendMessage: " + ex.getMessage());
    }
  }
  
}
