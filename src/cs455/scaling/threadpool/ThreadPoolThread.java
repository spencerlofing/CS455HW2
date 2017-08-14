package cs455.scaling.threadpool;

import cs455.scaling.RunnableTask.RunnableTaskQueue;
import cs455.scaling.RunnableTask.RunnableTask;

public class ThreadPoolThread implements Runnable{
	//maintains reference to the runnabletask queue so that each thread can retrieve tasks from it
	private RunnableTaskQueue queue;
	public ThreadPoolThread(RunnableTaskQueue queue){
		this.queue = queue;
	}
	public void run()
	{
		while(true)
		{
			RunnableTask rt;
			//synchronize access to the queue
			synchronized(queue)
			{
				//check if the queue has a size of zero
				while(queue.size() == 0)
				{
					//if queue size is zero, tell the thread to wait until it is notified by the server that it a task has been enqueued
					try
					{
						queue.wait();
					}
					catch(InterruptedException ie)
					{
						System.out.println(ie.getMessage());
					}
				}
				//once outside of the loop, retrieve the next runnable task from the task queue
				rt = queue.dequeue();
			}
			//call the run method on the retrieved runnable task
			rt.run();
		}
	}

}
