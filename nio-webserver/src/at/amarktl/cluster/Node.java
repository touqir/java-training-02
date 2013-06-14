
package at.amarktl.cluster;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.charset.Charset;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

public class Node extends UnicastRemoteObject implements IClusterNode {

  private static final String RMI_IDENTIFIER_NODE = "cluster-node";
  private static final String RMI_IDENTIFIER_MASTER = "web-srv-master";
  private static final long serialVersionUID = 1L;
  int port;
  String address;
  String name;

  private ExecutorService handles = null;

  /** {@inheritDoc} */
  @Override
  public String getIdentifier() throws RemoteException {
    return name;
  }

  @Override
  public void connect(String server, int serverport) throws RemoteException {
    initRMI();

    System.out.println("Cluster Node [" + name + "@" + address + ":" + port + "] initialized");

    register(server, serverport);
  }

  private void initRMI() {
    try {
      Registry registry = LocateRegistry.createRegistry(port);
      registry.rebind(RMI_IDENTIFIER_NODE, this);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void register(String host, int port) {
    try {
      System.out.println("Trying to register Cluster Node [" + name + "@" + address + ":" + this.port + "]  @ server [" + host + ":" + port + "]");
      Registry myRegistry = LocateRegistry.getRegistry(host, port);
      IServer master = (IServer) myRegistry.lookup(RMI_IDENTIFIER_MASTER);
      boolean registered = master.register(address, this.port);
      System.out.println("Finished to register Cluster Node [" + name + "@" + address + ":" + this.port + "]  @ server [" + host + ":" + port + "]: "
        + registered);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public Node(String name, String address, int port, int threadPoolSize) throws RemoteException {
    super();

    if (address == null) {
      throw new NullPointerException("'address' must not be null");
    }
    if (address.trim().length() == 0) {
      throw new IllegalArgumentException("'address' must not be empty");
    }

    if (name == null) {
      throw new NullPointerException("'name' must not be null");
    }
    if (name.trim().length() == 0) {
      throw new IllegalArgumentException("'name' must not be empty");
    }

    if (port <= 0) {
      throw new IllegalArgumentException("'port' must not be less or equal than 0");
    }

    if (threadPoolSize <= 0) {
      throw new IllegalArgumentException("'threadPoolSize' must not be less or equal than 0");
    }

    System.getProperties().setProperty("java.rmi.server.hostname", address);

    this.name = name;
    this.address = address;
    this.port = port;

    this.handles = Executors.newFixedThreadPool(threadPoolSize, new ThreadFactory() {
      @Override
      public Thread newThread(Runnable r) {
        return new Thread(r);
      }
    });
  }

  /** {@inheritDoc} */
  @Override
  public byte[] loadFile(String uri) throws RemoteException {
    if (uri == null) {
      Exception e = new NullPointerException("'uri' must not be null");
      throw new RemoteException(e.getMessage(), e);
    }
    if (uri.trim().length() == 0) {
      Exception e = new IllegalArgumentException("'uri' must not be empty");
      throw new RemoteException(e.getMessage(), e);
    }
    Future<byte[]> response = handles.submit(new Handle(uri));
    try {
      return response.get();
    } catch (Exception e) {
      throw new RemoteException(e.getMessage(), e);
    }
  }

  private class Handle implements Callable<byte[]> {

    private String uri;

    public Handle(String uri) {
      if (uri == null) {
        throw new NullPointerException("'uri' must not be null");
      }
      if (uri.trim().length() == 0) {
        throw new IllegalArgumentException("'uri' must not be empty");
      }
      this.uri = uri;
    }

    private String getIdentifier() {
      return Node.this.name + "@" + Node.this.address + ":" + Node.this.port;
    }

    /** {@inheritDoc} */
    @Override
    public byte[] call() throws Exception {

      //FIXME do not use 'user.dir' use some webhome setting...
      File f = new File(System.getProperty("user.dir") + uri);

      System.out.println("[" + getIdentifier() + "] loading file from URI [" + f.getAbsolutePath() + "]");

      BufferedReader reader = null;

      try {
        reader = new BufferedReader(new FileReader(f));
        StringBuilder response = new StringBuilder();
        String line = null;
        while ((line = reader.readLine()) != null) {
          response.append(line);
        }

        byte[] b = response.toString().getBytes(Charset.forName("UTF-8"));
        System.out.println("[" + name + "@" + address + ":" + port + "] finished loading file from URI [" + f.getAbsolutePath() + "]");
        return b;
      } finally {
        if (reader != null) {
          reader.close();
        }
      }
    }

  }

}
