package cs455.scaling.threadpool;

import java.util.LinkedList;

import cs455.scaling.RunnableTask.RunnableTaskQueue;

public class ThreadPoolManager {
	//reference to a linked list of threads used to process runnabletasks
	private LinkedList<ThreadPoolThread> threadPool;
	//constructor creates threads and starts them to process runnabletasks
	public ThreadPoolManager(int threadPoolSize, RunnableTaskQueue queue)
	{
		threadPool = new LinkedList<ThreadPoolThread>();
		for(int i = 0; i < threadPoolSize; i++)
		{
			ThreadPoolThread t = new ThreadPoolThread(queue);
			new Thread(t).start();
			threadPool.add(t);
		}
	}
}
