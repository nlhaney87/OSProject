package osp.Threads;
import java.util.Vector;
import java.util.Enumeration;
import osp.Utilities.*;
import osp.IFLModules.*;
import osp.Tasks.*;
import osp.EventEngine.*;
import osp.Hardware.*;
import osp.Devices.*;
import osp.Memory.*;
import osp.Resources.*;

/**
   This class is responsible for actions related to threads, including
   creating, killing, dispatching, resuming, and suspending threads.

   @OSPProject Threads
*/
public class ThreadCB extends IflThreadCB 
{
    private static GenericList foreground;
    private static GenericList background;
    private static GenericList intermediate;
    private static HTimer quantum;
    /**
       

       	   super();

       as its first statement.

       @OSPProject Threads
    */
    public ThreadCB()
    {
        // your code goes here
        super();
    }

    /**
       This method will be called once at the beginning of the
       simulation. The student can set up static variables here.
       
       @OSPProject Threads
    */
    public static void init()
    {
        // your code goes here
        foreground = new GenericList();
        intermediate = new GenericList();
        background = new GenericList();
        quantum = new HTimer();
    }

    /** 
        Sets up a new thread and adds it to the given task. 
        The method must set the ready status 
        and attempt to add thread to task. If the latter fails 
        because there are already too many threads in this task, 
        so does this method, otherwise, the thread is appended 
        to the ready queue and dispatch() is called.

	The priority of the thread can be set using the getPriority/setPriority
	methods. However, OSP itself doesn't care what the actual value of
	the priority is. These methods are just provided in case priority
	scheduling is required.

	@return thread or null

        @OSPProject Threads
    */
    static public ThreadCB do_create(TaskCB task)
    {
        // your code goes here
        if (task == null) {
            dispatch();
            return null;
        }

        if (task.getThreadCount() == MaxThreadsPerTask) {
            dispatch();
            return null;
        }

        ThreadCB thread = new ThreadCB();
        thread.setPriority(0);
        thread.setStatus(ThreadReady);
        thread.setTask(task);

        if (task.addThread(thread) == 0) {
            dispatch();
            return null;
        }

        foreground.append(thread);
        MyOut.print("osp.Threads.ThreadCB", "New thread placed in foreground queue");
        dispatch();
        return thread;
    }

    /** 
    Kills the specified thread. 

	The status must be set to ThreadKill, the thread must be
	removed from the task's list of threads and its pending IORBs
	must be purged from all device queues.
        
	If some thread was on the ready queue, it must removed, if the 
	thread was running, the processor becomes idle, and dispatch() 
	must be called to resume a waiting thread.
	
	@OSPProject Threads
    */
    public void do_kill()
    {
        // your code goes here
        TaskCB task = getTask();

        if (getStatus() == ThreadReady){
            if (foreground.remove(this) == null){
                if (intermediate.remove(this) == null){
                    background.remove(this);
                }
            }
        }
        else if (getStatus() == ThreadRunning) {
            if (this == MMU.getPTBR().getTask().getCurrentThread()) {
                getTask().setCurrentThread(null);
                MMU.setPTBR(null);
            }
        }

        setStatus(ThreadKill);
        ResourceCB.giveupResources(this);

        if (task.removeThread(this) != SUCCESS){
            return;
        }

        if (task.getThreadCount() == 0){
            task.kill();
        }

        for (int i = 0; i < Device.getTableSize(); i++) {
            Device.get(i).cancelPendingIO(this);
        }

        dispatch(); 
    }



    /** Suspends the thread that is currenly on the processor on the 
        specified event. 

        Note that the thread being suspended doesn't need to be
        running. It can also be waiting for completion of a pagefault
        and be suspended on the IORB that is bringing the page in.
	
	Thread's status must be changed to ThreadWaiting or higher,
        the processor set to idle, the thread must be in the right
        waiting queue, and dispatch() must be called to give CPU
        control to some other thread.

	@param event - event on which to suspend this thread.

        @OSPProject Threads
    */
    public void do_suspend(Event event)
    {
        // your code goes here
        if (getStatus() >= ThreadWaiting) {
            setStatus(getStatus()+1);
        }
        else {
            if (foreground.remove(this) == null){
                if (intermediate.remove(this) == null){
                    background.remove(this);
                }
            }
            if (this == MMU.getPTBR().getTask().getCurrentThread()) {
                getTask().setCurrentThread(null);
                MMU.setPTBR(null);
                this.setStatus(ThreadWaiting);
            }
        }


        event.addThread(this);
        dispatch();
    }

    /** Resumes the thread.
        
	Only a thread with the status ThreadWaiting or higher
	can be resumed.  The status must be set to ThreadReady or
	decremented, respectively.
	A ready thread should be placed on the ready queue.
	
	@OSPProject Threads
    */
    public void do_resume()
    {
        // your code goes here
        if (getStatus() < ThreadWaiting) {
            return;
        }
        else if (getStatus() == ThreadWaiting) {
            setStatus(ThreadReady);
        }
        else {
            setStatus(getStatus()-1);
        }

        if (getStatus() == ThreadReady){
            if (getPriority() > 5) {
                background.append(this);
            }
            else if (getPriority() > 1){
                intermediate.append(this);
            }
            else {
                foreground.append(this);
            }
        }
        
        dispatch();
    }

    /** 
        Selects a thread from the run queue and dispatches it. 

        If there is just one theread ready to run, reschedule the thread 
        currently on the processor.

        In addition to setting the correct thread status it must
        update the PTBR.
	
	@return SUCCESS or FAILURE

        @OSPProject Threads
    */
    public static int do_dispatch()
    {
        // your code goes here
        ThreadCB current = null;
        ThreadCB next = null;

        try {
            current = MMU.getPTBR().getTask().getCurrentThread();
        }
        catch (NullPointerException e){
            //do nothing
        }

        if (current != null){
            current.getTask().setCurrentThread(null);
            MMU.setPTBR(null);
            current.setStatus(ThreadReady);

            if (current.getPriority() > 5){
                background.append(current);
            }
            else if (current.getPriority() > 1){
                if (current.getPriority() == 5){
                    current.setPriority(current.getPriority()+1);
                    background.append(current);
                    MyOut.print("osp.Threads.ThreadCB", "Thread moved down to background queue");
                }
                else {
                    current.setPriority(current.getPriority()+1);
                    intermediate.append(current);
                }
            }
            else {
                if (current.getPriority() == 1){
                    current.setPriority(current.getPriority()+1);
                    intermediate.append(current);
                    MyOut.print("osp.Threads.ThreadCB", "Thread moved down to intermediate queue");
                }
                else {
                    current.setPriority(current.getPriority()+1);
                    foreground.append(current);
                }
            }
        }
        
            if (!foreground.isEmpty()){
                next = (ThreadCB)foreground.removeHead();
                MMU.setPTBR(next.getTask().getPageTable());
                next.getTask().setCurrentThread(next);
                next.setStatus(ThreadRunning);
                quantum.set(4);
            }
            else if (!intermediate.isEmpty()){
                next = (ThreadCB)intermediate.removeHead();
                MMU.setPTBR(next.getTask().getPageTable());
                next.getTask().setCurrentThread(next);
                next.setStatus(ThreadRunning);
                quantum.set(8);
            }
            else if (!background.isEmpty()){
                next = (ThreadCB)background.removeHead();
                MMU.setPTBR(next.getTask().getPageTable());
                next.getTask().setCurrentThread(next);
                next.setStatus(ThreadRunning);
                quantum.set(12);
            }
            else {
                MMU.setPTBR(null);
                return FAILURE;
            }
        
        return SUCCESS;
    }

    /**
       Called by OSP after printing an error message. The student can
       insert code here to print various tables and data structures in
       their state just after the error happened.  The body can be
       left empty, if this feature is not used.

       @OSPProject Threads
    */
    public static void atError()
    {
        // your code goes here

    }

    /** Called by OSP after printing a warning message. The student
        can insert code here to print various tables and data
        structures in their state just after the warning happened.
        The body can be left empty, if this feature is not used.
       
        @OSPProject Threads
     */
    public static void atWarning()
    {
        // your code goes here

    }


    /*
       Feel free to add methods/fields to improve the readability of your code
    */

}

/*
      Feel free to add local classes to improve the readability of your code
*/
