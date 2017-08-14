package cs455.scaling.RunnableTask;

import java.util.LinkedList;
import java.util.Queue;

import cs455.scaling.threadpool.ThreadPoolThread;

//class that essentially wraps around a queue of runnabletask objects
public class RunnableTaskQueue {
	private Queue<RunnableTask> taskQueue;
	public RunnableTaskQueue()
	{
		taskQueue = new LinkedList<RunnableTask>();
	}
	//adds the inputed runnabletask to the end of the underlying queue
	public void enqueue(RunnableTask rt)
	{
		taskQueue.add(rt);
	}
	//removes a runnabletask from the front of the underlying queue and returns it to the caller
	public RunnableTask dequeue()
	{
		return taskQueue.remove();
	}
	//returns the number of elements in the underlying queue
	public int size()
	{
		return taskQueue.size();
	}
}
