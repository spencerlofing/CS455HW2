package cs455.scaling.client;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;

//make class runnable, so that two separate threads can exist
//one for keeping track of time and one for receiving and sending messages
public class Client implements Runnable{
	public static void main(String[] args) {
		//store arguments from command line as variables
		String serverHost = args[0];
		int serverPort = Integer.parseInt(args[1]);
		int messageRate = Integer.parseInt(args[2]);
		//create client with command line arguments
		Client client = new Client(serverHost, serverPort, messageRate);
		//start the client thread
		new Thread(client).start();
		//keep track of time and print a summary every ten seconds
		long startTime = System.nanoTime();
		while(true)
		{
			long currentTime = System.nanoTime();
			if((currentTime - startTime)/(1000000000) >= 10)
			{
				client.printSummary();
				startTime = currentTime;
			}
		}
	}
	//collection of local variables
	//variables from the command line 
	private String serverHost;
	private int serverPort;
	private int messageRate;
	//socket for connections
	private SocketChannel socketChannel;
	//selector for iterating through keys
	private Selector selector;
	//linked list for keeping track of the sha1 of byte arrays sent to the server
	private LinkedList<String> sha1Store;
	//int that keeps track of the size of each byte array to be allocated
	private int messageSize;
	//keeps track of messages sent and received for logging
	private int numberSent;
	private int numberReceived;
	public Client(String serverHost, int serverPort, int messageRate)
	{
		//set local variables
		this.serverHost = serverHost;
		this.serverPort = serverPort;
		this.messageRate = messageRate;
		sha1Store = new LinkedList<String>();
		//initialize message log
		numberSent = 0;
		numberReceived = 0;
	}
	
	public void run()
	{
		try
		{
			//initialize all selector
			selector = Selector.open();
			//open socketchannel
			socketChannel = SocketChannel.open();
			//setup socketchannel for nio
			socketChannel.configureBlocking(false);
			//register the socketchannel with the selector
			socketChannel.register(selector, SelectionKey.OP_CONNECT);
			//connect the socket to the server
			socketChannel.connect(new InetSocketAddress(serverHost, serverPort));
			
			//infinite loop for connection
			while(true)
			{
				//select a set of selectionkeys that are ready for nio operations
				selector.select();
				//establish an iterator to move through this set
				Iterator selectedKeys = selector.selectedKeys().iterator();
				//move through iterator while there are still operations to perform
				while(selectedKeys.hasNext())
				{
					//set interested key to a variable
					SelectionKey key = (SelectionKey) selectedKeys.next();
					//remove the key from the iterator
					selectedKeys.remove();
					//check if the key is interested in making a connection
					if(key.isConnectable())
					{
						//call connect method
						connect(key);
					}
					//check if the key is interested in writing to its socket channel
					else if(key.isWritable())
					{
						//call write method
						write(key);
					}
					//check if the key is interested in reading from its socket channel
					else if(key.isReadable())
					{
						//call read method
						read(key);
						//make client sleep after receiving a response to implement the specified message rate
						try
						{
							Thread.sleep(1000/messageRate);
						}
						catch(InterruptedException ie)
						{
							System.out.println(ie.getMessage());
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
	private void connect(SelectionKey key) throws IOException
	{
		//get the inputed key's socket channel
		SocketChannel channel = (SocketChannel) key.channel();
		//if the channel has not fully connected to the endpoint, complete the connection
		channel.finishConnect();
		//set the key as being interested in writing to the channel
		key.interestOps(SelectionKey.OP_WRITE);
	}
	
	private void write(SelectionKey key) throws IOException
	{
		//get the inputed key's socket channel
		SocketChannel channel = (SocketChannel) key.channel();
		//allocate a new random for generating a random 8 kb byte array
		Random r = new Random();
		byte[] data = new byte[8000];
		r.nextBytes(data);
		
		//establish a bytebuffer around the random byte array
		ByteBuffer buffer = ByteBuffer.wrap(data);
	
		//write the bytebuffer to the socket channel
		channel.write(buffer);
		try
		{
			//compute the sha1 from byte array
			String sha1 = SHA1FromBytes(data);
			//get size of message to decide on size of byte buffer to allocate when reading
			messageSize = sha1.length();
			//add the computed sha1 string to the list of sha1s
			sha1Store.add(sha1);
		}
		catch(NoSuchAlgorithmException nsae)
		{
			System.out.println(nsae.getMessage());
		}
		//set the key's interest to read
		key.interestOps(SelectionKey.OP_READ);
		//increment the sent counter to record this message as having been sent
		numberSent++;
	}
	
	private void read(SelectionKey key) throws IOException
	{
		//get the inputed key's socket channel
		SocketChannel channel = (SocketChannel) key.channel();
		//allocate a byte buffer the size of the last message sent
		ByteBuffer buffer = ByteBuffer.allocate(messageSize);
		int read = 0;
		try
		{
			//while the buffer still has bytes and the reading doesn't return an error, read from the channel into the buffer
			while(buffer.hasRemaining() && read != -1)
			{
				read = channel.read(buffer);
			}
		}
		//if there's an IOException deregister the key and close the socket channel
		catch(IOException ioe)
		{
			System.out.println(ioe.getMessage());
			key.cancel();
			socketChannel.close();
			return;
		}
		//if there's an error reading from the channel, deregister the ky and close the socket channel
		if(read == -1)
		{
			socketChannel.close();
			key.cancel();
			return;
		}
		//read the byte buffer into a byte array
		byte[] data = buffer.array();
		//read the byte array into a string
		String sha1 = new String(data);
		//check if the list of sha1s contains the received string
		if(sha1Store.contains(sha1))
		{
			sha1Store.remove(sha1);
		}
		//set the key's interest to write
		key.interestOps(SelectionKey.OP_WRITE);
		//increment the count of received messages
		numberReceived++;
	}
	//method to compute sha1 from a byte array
	private String SHA1FromBytes(byte[] data) throws NoSuchAlgorithmException
	{
		MessageDigest digest = MessageDigest.getInstance("SHA1");
		byte[] hash = digest.digest(data);
		BigInteger hashInt = new BigInteger(1, hash);
		return hashInt.toString(16);
	}
	//method to print out a timestamp then the sent and received count
	//reset the number sent and received each time this method is called
	public void printSummary()
	{
		String timeStamp = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(new Date());
		System.out.println("[" + timeStamp + "] Total Sent Count: " + numberSent + ", Total Received Count: " + numberReceived);
		numberSent = 0;
		numberReceived = 0;
	}
}
