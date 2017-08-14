This program is broken into four packages:
    1. cs455.scaling.client encompasses the client functionality with the solitary file: Client.java. The Client class attempts to make an outgoing connection with a specified server identified by its IP Address and port number. The client then sends messages to the server at a rate designated by the user, stores the messages as a sha1 string, then, upon receiving a sha1 response from the server, verifies that the response matches the sha1 of a message which was sent.

    2. cs455.scaling.server encompasses the server functionality with the solitary file Server.java. The Server class polls for incoming connections using non-blocking-I/O. The server will assign a key for each connection and register it with its lone socket. The server then iterates through each connection to read from a particular key's channel and send a response message on that channel. The read and write operations are sent to a taskqueue to be handled by the threadpool.

    3. cs455.scaling.RunnableTask contains four classes:
        a. RunnableTask.java is a simple interface that only contains a run method to be implemented by classes which inherit from it: ReadFromChannel.java and WriteToChannel.java. The purpose of using an interface was to make it simple to obscure the specific nature of a task from the threadpool before an individual thread had selected a task.
        b. ReadFromChannel.java contains an externally accessible run method to be used by a thread from the threadpool. This run method reads a byte array from a particular key's channel, computes the sha1 string for the byte array and stores the sha1 string in a hashmap for later use.
        c. WriteToChannel.java contains an externally accessible run method to be used by a thread from the threadpool. This run method accesses a sha1 string computed by a read task, converts the sha1 to a byte array then sends the array over a particular key's channel to be received by the client.
        d. RunnableTaskQueue.java encapsulates the necessary methods for a queue and stores RunnableTask objects. It features the methods: enqueue, dequeue, and size.

    4. cs455.scaling.threadpool contains two classes:
        a. ThreadPoolManager.java serves the functionality of creating a starting ThreadPoolThreads and is utilized by the Server class.
        b. ThreadPoolThread.java is a thread that maintains a reference to the RunnableTaskQueue, attempts to obtain a lock on it, checks its size then waits for the server thread to notify() it if the RunnableTaskQueue is empty. When it is notified of a new RunnableTask being ready for execution, it wakes up and (assuming the RunnableTaskQueue is not empty) calls the run method for the waiting RunnableTask.
