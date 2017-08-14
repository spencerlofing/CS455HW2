package cs455.scaling.RunnableTask;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Map;
import java.util.Queue;

import cs455.scaling.server.Server;

public class WriteToChannel implements RunnableTask, Runnable {
	private volatile SelectionKey key;
	private Map<SocketChannel, Queue<String>> channelToSha1;
	private Server server;
	private Selector selector;
	public WriteToChannel(SelectionKey key, Map<SocketChannel, Queue<String>> channelToSha1, Server server, Selector selector)
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
			//get the key's associated socket channel
			SocketChannel channel = (SocketChannel) key.channel();
			String sha1;
			//synchronize access to the (SocketChannel, queue of sha1s) and remove the front sha1 from the list for the given socketchannel 
			synchronized(channelToSha1)
			{
				sha1 = channelToSha1.get(channel).remove();
			}
			//turn the selected sha1 string into a byte array
			byte[] data = sha1.getBytes();
		
			//wrap a byte buffer around the byte array
			ByteBuffer buffer = ByteBuffer.wrap(data);
			//write the byte buffer to the socket channel
			channel.write(buffer);
			//increment the number of processed messages the server has gone through
			server.incrementProcessed();
			//set the key's interest op to read
			key.interestOps(SelectionKey.OP_READ);
			//tell the selector to check for new interest ops
			selector.wakeup();
		}
		catch(IOException ioe)
		{
			System.out.println(ioe.getMessage());
		}
	}
}
