package cs455.scaling.RunnableTask;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

import cs455.scaling.server.Server;

public class ReadFromChannel implements RunnableTask, Runnable{
	//local variables that contain references to objects created on the server
	private SelectionKey key;
	private Map<SocketChannel, Queue<String>> channelToSha1;
	private Server server;
	private Selector selector;
	public ReadFromChannel(SelectionKey key, Map<SocketChannel, Queue<String>> channelToSha1, Server server, Selector selector)
	{
		this.key = key;
		this.channelToSha1 = channelToSha1;
		this.server = server;
		this.selector = selector;
	}
	public void run()
	{
		try
		{
			//get key's associated socket channel
			SocketChannel channel = (SocketChannel) key.channel();
			//allocate a byte buffer the size of 8 kb
			ByteBuffer buffer = ByteBuffer.allocate(8000);
			int read = 0;
			try
			{
				//while channel still has bytes remaining and there are no errors in reading, read from the channel into the byte buffer
				while(buffer.hasRemaining() && read != -1)
				{
					read = channel.read(buffer);
				}
			}
			//if there is an IOException, deregister the key, close the channel, and decrement the number of clients registered with the server
			catch(IOException ioe)
			{
				System.out.println(ioe.getMessage());
				key.cancel();
				channel.close();
				server.decrementClientCount();
				return;
			}
			//if there is an error in reading from the channel, deregister the key, close the channel, and decrement the number of clients registered with the server
			if(read == -1)
			{
				channel.close();
				key.cancel();
				server.decrementClientCount();
				return;
			}
			//get a byte array from the byte buffer
			byte[] data = buffer.array();
			try
			{
				//read a string from the byte array
				String sha1 = new String(SHA1FromBytes(data));
				//synchronize access to the hashmap containing (SocketChannel, queue of sha1s)
				synchronized(channelToSha1)
				{
					//if there is not reference to a queue in the specified location of the hashmap, create a new queue, add a sha1 string to the queue, and add the queue to the hashmap
					if(channelToSha1.get(channel) == null)
					{
						Queue<String> sha1queue = new LinkedList<String>();
						sha1queue.add(sha1);
						channelToSha1.put(channel, sha1queue);
					}
					//otherwise, add the sha1 string to the socketchannel's queue in the hashmap
					else
					{
						channelToSha1.get(channel).add(sha1);
					}
				}
			}
			catch(NoSuchAlgorithmException nsae)
			{
				System.out.println(nsae.getMessage());
			}
			//set the selectionkey's interest op to write
			key.interestOps(SelectionKey.OP_WRITE);
			//inform the selector to check for changes in the set of selectionkeys
			selector.wakeup();
		}
		catch(IOException ioe)
		{
			System.out.println(ioe.getMessage());
		}
	}
	//compute sha1 from a byte array
	private String SHA1FromBytes(byte[] data) throws NoSuchAlgorithmException
	{
		MessageDigest digest = MessageDigest.getInstance("SHA1");
		byte[] hash = digest.digest(data);
		BigInteger hashInt = new BigInteger(1, hash);
		return hashInt.toString(16);
	}

}
