package cs455.scaling.server;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;

import cs455.scaling.RunnableTask.ReadFromChannel;
import cs455.scaling.RunnableTask.RunnableTaskQueue;
import cs455.scaling.RunnableTask.WriteToChannel;
import cs455.scaling.threadpool.ThreadPoolManager;

public class Server implements Runnable{
	public static void main(String[] args) {
		//read in a port number and thread pool size from the cli
		int portNumber = Integer.parseInt(args[0]);
		int threadPoolSize = Integer.parseInt(args[1]);
		//construct new server object from the command line arguments
		Server server = new Server(portNumber, threadPoolSize);
		new Thread(server).start();
		//keep track of time and print the server's throughput every five seconds
		long startTime = System.nanoTime();
		while(true)
		{
			long currentTime = System.nanoTime();
			if((currentTime - startTime)/(1000000000) >= 5)
			{
				server.printSummary();
				startTime = currentTime;
			}
		}
	}
	//local variables for commandline arguments
	private int portNumber;
	private int threadPoolSize;
	//reference to the server socket
	private ServerSocketChannel serverChannel;
	private Selector selector;
	//maps socketchannels to a queue of sha1s, waiting to be sent back to the client
	private Map<SocketChannel, Queue<String>> channelToSha1;
	//queue of runnable tasks for the threadpool
	private RunnableTaskQueue queue;
	//provides a way to start a threadpool
	private ThreadPoolManager threadPool;
	//keeps track of the number of messages processed by the server
	private Integer numberProcessed;
	//keeps track of the number of active connections
	private int numberOfClients;
	//keeps track of whether a selection key has been signalled to read
	private Map<SelectionKey, Boolean> keyToReadState;
	//keeps track of whether a selection key has been signalled to write
	private Map<SelectionKey, Boolean> keyToWriteState;
	
	public Server(int portNumber, int threadPoolSize)
	{
		this.threadPoolSize = threadPoolSize;
		this.portNumber = portNumber;
		channelToSha1 = new HashMap();
		queue = new RunnableTaskQueue();
		threadPool = new ThreadPoolManager(threadPoolSize, queue);
		numberProcessed = 0;
		numberOfClients = 0;
		keyToReadState = new HashMap();
		keyToWriteState = new HashMap();
	}
	public void run()
	{
		try
		{
			//initialize selector
			selector = Selector.open();
			//initialize serverSocketChannel
			serverChannel = ServerSocketChannel.open();
			//open up a new internet socket address on the specified port number
			InetSocketAddress isa = new InetSocketAddress(portNumber);
			//reference to a the serverSocketChannel's socket
			ServerSocket socket = serverChannel.socket();
			//bind the socket to the new internet socket address
			socket.bind(isa);
			//setup the serverSocketChannel for nio
			serverChannel.configureBlocking(false);
			//register the socketchannel on the given selector and set the interest op to accept
			serverChannel.register(selector, SelectionKey.OP_ACCEPT);
			//print out where the server was created
			System.out.println("Opened server on " + isa.getAddress().getLocalHost().toString() + ":" + socket.getLocalPort());
			
			while(true)
			{
				//choose a set of selectionKeys with waiting nio operations
				selector.select();
				//create an iterator through this set of selectionkeys
				Iterator<SelectionKey> selectedKeys = selector.selectedKeys().iterator();
				//while there are still nio operations to be done on the set
				while(selectedKeys.hasNext())
				{
					//get a reference to the next selectionkey in the iterator
					SelectionKey key = (SelectionKey) selectedKeys.next();
					//remove the key from the iterator
					selectedKeys.remove();
	
					//if the key is not valid, skip the key
					if(!key.isValid())
					{
						continue;
					}
					//if the key is ready to accept incoming connections
					if(key.isAcceptable())
					{
						//call accept method
						accept(key);
					}
					//check if key is ready to read on its channel
					else if(key.isReadable())
					{
						//if the hashmap on the key returns no value or false, allow a read task to be created
						if(keyToReadState.get(key) == null || keyToReadState.get(key) == false)
						{
							//set the read boolean to true for this key to indicate a read task has been created for it
							keyToReadState.put(key, true);
							//call read method on key
							read(key);
							//set the write state to false to indicate there is no current write task set for this key
							keyToWriteState.put(key, false);
						}
					}
					//check if key is read to write to its channel
					else if(key.isWritable())
					{
						//if the hashmap on the key returns no value or false, allow a write task to be created
						if(keyToWriteState.get(key) == null || keyToWriteState.get(key) == false)
						{
							//set the write boolean to true for this key to indicate a write task has been created for it
							keyToWriteState.put(key, true);
							//call write method on key
							write(key);
							//set the read state to false to indicate there is no current read task set for this key
							keyToReadState.put(key, false);
						}
					}
				}
			}
		}
		catch(IOException ioe)
		{
			System.out.println(ioe.getMessage());
		}
	}
	//method used for establishing connection with a client
	private void accept(SelectionKey key) throws IOException
	{
		//increment the number of client connections on the server
		numberOfClients++;
		//reference to the channel on which the key is registered
		ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
		//accept a connection on this channel
		SocketChannel socketChannel = serverSocketChannel.accept();
		//set up nio on this channel
		socketChannel.configureBlocking(false);
		//register this channel with the selector and set its interest op to read
		socketChannel.register(selector, SelectionKey.OP_READ);	
	}
	//method used for reading from the key's channel
	private void read(SelectionKey key) throws IOException
	{
		//create a new runnable task that indicates a read from channel
		ReadFromChannel rfc = new ReadFromChannel(key, channelToSha1, this, selector);
		//synchronize access to the runnabletask queue and add this new read task to the queue
		synchronized(queue)
		{
			queue.enqueue(rfc);
			//notify all threads waiting on the queue to wakeup
			queue.notifyAll();
		}
	}
	//method used for writing to the key's channel
	private void write(SelectionKey key) throws IOException
	{
		//create a new runnable task that indicates a write to channel
		WriteToChannel wtc = new WriteToChannel(key, channelToSha1, this, selector);
		//synchronize access to the runnabletask queue and add this new write task to the queue
		synchronized(queue)
		{
			queue.enqueue(wtc);
			//notify all threads waiting on the queue to wakeup
			queue.notifyAll();
		}
	}
	//method to increment the count of the number of messages processed
	public synchronized void incrementProcessed()
	{
		numberProcessed++;
	}
	//method to decrement the count of the number of client connections
	public synchronized void decrementClientCount()
	{
		numberOfClients--;
	}
	//method to print a summary of the server's throughput
	public void printSummary()
	{
		//synchronize access to the count of the number of messages processed
		synchronized(numberProcessed)
		{
			String timeStamp = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(new Date());
			System.out.println("[" + timeStamp + "] Current Server Throughput: " + numberProcessed/5 + " messages/s, Active Client Connections: " + numberOfClients);
			//reset the count of the number of messages processed when this method is called
			numberProcessed = 0;
		}
	}
	
}
